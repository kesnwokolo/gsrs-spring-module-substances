package gsrs.module.substance.controllers;

import gsrs.controller.GsrsControllerConfiguration;
import gsrs.module.substance.services.ProcessingJobEntityService;
import gsrs.module.substance.services.SubstanceBulkLoadService;
import gsrs.payload.PayloadController;
import gsrs.repository.PayloadRepository;
import gsrs.security.hasAdminRole;
import gsrs.service.PayloadService;
import ix.core.models.Payload;
import ix.core.models.ProcessingJob;
import ix.core.processing.PayloadProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class SubstanceLegacyBulkLoadController {

    @Autowired
    private PayloadService payloadService;

    @Autowired
    private PayloadRepository payloadRepository;

    @Autowired
    private SubstanceBulkLoadService substanceBulkLoadService;

    @Autowired
    private GsrsControllerConfiguration controllerConfiguration;

    @Autowired
    private ProcessingJobEntityService processingJobService;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;



    @hasAdminRole
    @GetMapping("/api/v1/admin/{id}")
    public Object getLoadStatus(@PathVariable("id") String id, @RequestParam Map<String, String> queryParameters){
        Optional<ProcessingJob> jobs = processingJobService.flexLookup(id);
        if(!jobs.isPresent()){
            return controllerConfiguration.handleNotFound(queryParameters);
        }
        return jobs.get();
    }

    ///admin/load
    @Transactional
    @hasAdminRole
    @PostMapping("/api/v1/admin/load")
    public Object handleFileUpload(@RequestParam("file-name") MultipartFile file,
                                                   @RequestParam("file-type") String type,
                                                   @RequestParam Map<String, String> queryParameters) throws IOException {
        try {


            //legacy GSRS 2.x only supported JSON we turned of sd support in this method at some point
            //between 2.0 and 2.7 instead waiting for the new importer in 3.x to be written in a more robust way.
            if (!"JSON".equals(type)) {
                return controllerConfiguration.handleBadRequest("invalid file type:" + type, queryParameters);
            }
            //the payload needsto be created in a separate transaction so we can reference it
            //in other transactions in a multithreaded way


            TransactionTemplate transactionTemplate = new TransactionTemplate(platformTransactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            UUID payloadId = transactionTemplate.execute(status -> {
                try {
                    return payloadService.createPayload(file.getOriginalFilename(), PayloadController.predictMimeTypeFromFile(file),
                            file.getBytes(), PayloadService.PayloadPersistType.TEMP).id;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

                Payload payload = payloadRepository.findById(payloadId).get();
                //Beta UI sets this to true only if checked otherwise it's passed in as false
                boolean preserveAuditInfo = Boolean.parseBoolean(queryParameters.getOrDefault("preserve-audit", "false"));

                PayloadProcessor processor = substanceBulkLoadService.submit(
                        SubstanceBulkLoadService.SubstanceBulkLoadParameters.builder()
                                .payload(payload)
                                .preserveOldEditInfo(preserveAuditInfo)
                                .build());

            return processingJobService.get(processor.jobId).get();
        }catch(Throwable t){
            t.printStackTrace();
            throw t;
        }
    }
}
