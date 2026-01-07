package fr.mael3il.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.context.ThreadContext;
import org.neo4j.driver.Driver;
import org.neo4j.driver.async.AsyncSession;

import java.util.concurrent.CompletionStage;

@Path("api/v1/dashboard")
@Consumes("application/json")
@Produces("application/json")
public class ApiDashboard {

    @Inject
    Driver driver;

    @Inject
    ThreadContext threadContext;

    //récuupère le nombre de parcelles, capteurs, robots, observations dans la base de données et le retourne sous forme de json sans passer par un objet intermédiaire
    @GET
    @Path("stats")
    public CompletionStage<Response> getDashboardStats() {
        AsyncSession session = driver.session(AsyncSession.class);

        CompletionStage<String> cs = session
                .executeReadAsync(tx -> tx
                        .runAsync(
                                "MATCH (p:Parcelle) " +
                                        "WITH count(p) AS nbParcelles " +
                                        "MATCH (c:Capteur) " +
                                        "WITH nbParcelles, count(c) AS nbCapteurs " +
                                        "MATCH (r:Robot) " +
                                        "WITH nbParcelles, nbCapteurs, count(r) AS nbRobots " +
                                        "MATCH (o:Observation) " +
                                        "RETURN nbParcelles, nbCapteurs, nbRobots, count(o) AS nbObservations"
                        )
                        .thenCompose(cursor ->
                                cursor.singleAsync()
                                        .thenApply(record -> {
                                            long parcelleCount = record.get("nbParcelles").asLong();
                                            long capteurCount = record.get("nbCapteurs").asLong();
                                            long robotCount = record.get("nbRobots").asLong();
                                            long observationCount = record.get("nbObservations").asLong();

                                            return String.format(
                                                    "{\"parcelles\": %d, \"capteurs\": %d, \"robots\": %d, \"observations\": %d}",
                                                    parcelleCount, capteurCount, robotCount, observationCount
                                            );
                                        })
                        ));

        return threadContext.withContextCapture(cs)
                .thenCompose(statsJson ->
                        session.closeAsync().thenApply(signal -> statsJson))
                .thenApply(Response::ok)
                .thenApply(Response.ResponseBuilder::build);
    }
}