package gsrs.module.substance.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import gsrs.cache.GsrsCache;
import gsrs.controller.*;
import gsrs.module.substance.services.ReindexFromBackups;
import gsrs.security.hasAdminRole;
import gsrs.util.TaskListener;
import ix.core.EntityMapperOptions;
import ix.ginas.models.v1.Substance;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.ExposesResourceFor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import javax.persistence.Id;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@ExposesResourceFor(ReIndexController.ReindexStatus.class)
@GsrsRestApiController(context = "reindex",  idHelper = IdHelpers.UUID)
public class ReIndexController {

    @Autowired
    private GsrsCache ixCache;

    @Autowired
    private ReindexFromBackups reindexService;

    @Autowired
    private GsrsControllerConfiguration gsrsControllerConfiguration;

    @Data
    @EntityMapperOptions()
    public static class ReindexStatus {
        @JsonProperty("status")
        private TaskListener listener;
        @Id
        private UUID uuid;

        private long total;

        private AtomicLong count= new AtomicLong(0);

        public void incrementCount(){
            count.incrementAndGet();
        }

        public String generateMessage(){
            return count.get() + " of "+ total;
        }
    }

    @GetGsrsRestApiMapping({"({ID})","/{ID}"})
    public Object get(@PathVariable("ID") UUID id, @RequestParam Map<String,String> queryParameters){
        ReIndexController.ReindexStatus status = (ReIndexController.ReindexStatus) ixCache.getTemp(id.toString());
        if(status ==null){
            return gsrsControllerConfiguration.handleNotFound(queryParameters);
        }

        return GsrsControllerUtil.enhanceWithView(status, queryParameters);
    }

    @PostGsrsRestApiMapping("/@reindex")
    @hasAdminRole
    public Object reindex( @RequestParam Map<String, String> queryParameters) throws IOException {
        //TODO should we post a BODY with re-index params?
        ReIndexController.ReindexStatus status = new ReIndexController.ReindexStatus();
        status.setUuid(UUID.randomUUID());
        ixCache.setTemp(status.uuid.toString(), status);
        TaskListener listener = new TaskListener();
        status.setListener(listener);
        reindexService.executeAsync(status.uuid, listener);

        return GsrsControllerUtil.enhanceWithView(status, queryParameters);
    }

   
}
