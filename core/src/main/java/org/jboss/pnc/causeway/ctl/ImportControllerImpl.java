package org.jboss.pnc.causeway.ctl;

import org.jboss.pnc.causeway.CausewayException;
import org.jboss.pnc.causeway.CausewayFailure;
import org.jboss.pnc.causeway.brewclient.BrewClient;
import org.jboss.pnc.causeway.brewclient.BuildTranslator;
import org.jboss.pnc.causeway.brewclient.ImportFileGenerator;
import org.jboss.pnc.causeway.config.CausewayConfig;
import static org.jboss.pnc.causeway.ctl.PncImportControllerImpl.messageMissingTag;
import org.jboss.pnc.causeway.rest.BrewBuild;
import org.jboss.pnc.causeway.rest.BrewNVR;
import org.jboss.pnc.causeway.rest.CallbackTarget;
import org.jboss.pnc.causeway.rest.model.Build;
import org.jboss.pnc.causeway.rest.model.response.BuildRecordPushResultRest;
import org.jboss.pnc.causeway.rest.model.response.BuildRecordPushResultRest.BuildRecordPushResultRestBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.redhat.red.build.koji.model.json.KojiImport;

import lombok.Data;

/**
 *
 * @author Honza Brázdil &lt;jbrazdil@redhat.com&gt;
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ImportControllerImpl implements ImportController {

    private final Logger logger = Logger.getLogger(ImportControllerImpl.class.getName());

    @Inject
    private BrewClient brewClient;
    @Inject
    private BuildTranslator translator;
    @Inject
    private CausewayConfig config;
    private ResteasyClient restClient;

    @Inject
    public ImportControllerImpl() {
        restClient = new ResteasyClientBuilder().connectionPoolSize(4).build();
    }

    @Override
    @Asynchronous
    public void importBuild(Build build, CallbackTarget callback) {
        logger.log(Level.INFO, "Entering importBuild.");

        BuildRecordPushResultRestBuilder response = BuildRecordPushResultRest.builder();
        response.buildRecordId(build.getExternalBuildID());
        try {
            BuildResult result = importBuild(build, build.getTagPrefix());
            response.brewBuildId(result.getBrewID());
            response.brewBuildUrl(result.getBrewURL());
            response.status(BuildRecordPushResultRest.Status.SUCCESS);
            response.log(result.getMessage());
        } catch (CausewayFailure ex) {
            logger.log(Level.SEVERE, "Failed to import build.", ex);
            response.status(BuildRecordPushResultRest.Status.FAILED);
            response.artifactImportErrors(ex.getArtifactErrors());
            response.log(ex.getMessage());
        } catch (CausewayException ex) {
            logger.log(Level.SEVERE, "Error while importing build.", ex);
            response.status(BuildRecordPushResultRest.Status.SYSTEM_ERROR);
            response.log(ex.getMessage());
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "Error while importing build.", ex);
            response.status(BuildRecordPushResultRest.Status.SYSTEM_ERROR);
            response.log(ex.getMessage());
        }
        respond(callback, response.build());
    }

    private BuildResult importBuild(Build build, String tagPrefix) throws CausewayException {
        if (build.getBuiltArtifacts().isEmpty()) {
            throw new CausewayFailure("Build doesn't contain any artifacts");
        }
        if (!brewClient.tagsExists(tagPrefix)) {
            throw new CausewayFailure(messageMissingTag(tagPrefix, config.getKojiURL()));
        }

        BrewNVR nvr = getNVR(build);

        BrewBuild brewBuild = brewClient.findBrewBuildOfNVR(nvr);
        String message;
        if (brewBuild == null) {
            KojiImport kojiImport = translator.translate(nvr, build);
            ImportFileGenerator importFiles = translator.getImportFiles(build);

            brewBuild = brewClient.importBuild(nvr, kojiImport, importFiles);
            message = "Build imported with id ";
        } else {
            message = "Build was already imported with id ";
        }

        brewClient.tagBuild(tagPrefix, getNVR(build));

        return new BuildResult(brewBuild.getId(), brewClient.getBuildUrl(brewBuild.getId()),
                message + brewBuild.getId());
    }

    private BrewNVR getNVR(Build build) {
        return new BrewNVR(build.getBuildName(), build.getBuildVersion(), "1");
    }

    private void respond(CallbackTarget callback, BuildRecordPushResultRest build) {
        logger.log(Level.INFO, "Will send callback to {0}.", callback.getUrl());
        ResteasyWebTarget target = restClient.target(callback.getUrl());
        Invocation.Builder request = target.request(MediaType.APPLICATION_JSON);
        callback.getHeaders().forEach(request::header);
        Response response = request.post(Entity.entity(build, MediaType.APPLICATION_JSON_TYPE));
        logger.log(Level.INFO, "Callback response: {0} - {1}", new Object[]{response.getStatusInfo(), response.readEntity(String.class)});
    }

    @Data
    public static class BuildResult {

        private final int brewID;
        private final String brewURL;
        private final String message;
    }
}
