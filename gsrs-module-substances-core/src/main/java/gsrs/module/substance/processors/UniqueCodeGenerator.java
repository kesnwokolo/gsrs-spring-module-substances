package gsrs.module.substance.processors;

import gov.nih.ncats.common.util.CachedSupplier;
import gsrs.cv.api.*;
import gsrs.springUtils.AutowireHelper;
import ix.core.EntityProcessor;
import ix.ginas.models.v1.*;
import ix.ginas.utils.CodeSequentialGenerator;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
public class UniqueCodeGenerator implements EntityProcessor<Substance> {

    private CachedSupplier<CodeSequentialGenerator> seqGen = null;
    private final String codeSystem;
    private final String CODE_SYSTEM_VOCABULARY = "CODE_SYSTEM";

    private final CachedSupplier initializer = CachedSupplier.runOnceInitializer(this::addCodeSystem);

    @Autowired
    private ControlledVocabularyApi cvApi;


    public UniqueCodeGenerator(Map with) {
        log.trace("UniqueCodeGenerator constructor with Map");
        String name = (String) with.get("name");
        codeSystem = (String) with.get("codesystem");
        String codeSystemSuffix = (String) with.get("suffix");
        //Long last = (Long) with.computeIfPresent("last", (key, val) -> ((Number) val).longValue());
        int length = (Integer) with.get("length");
        boolean padding = (Boolean) with.get("padding");
        String msg = String.format("codeSystem: %s; codeSystemSuffix: %s; length: %d;  padding: %b",
                codeSystem, codeSystemSuffix, length, padding);
        log.trace(msg);
        if (codeSystem != null) {
            seqGen = CachedSupplier.runOnce(()->{
                CodeSequentialGenerator gen= new CodeSequentialGenerator(name, length, codeSystemSuffix, padding, codeSystem);//removed 'last'
                AutowireHelper.getInstance().autowire(gen);
                return gen;
            });
        }
    }

    private void addCodeSystem() {
        log.trace("addCodeSystem and gone");
        try {
            addCodeSystemIfNecessary();
        }catch(Throwable t){
            t.printStackTrace();
        }
    }

    public void generateCodeIfNecessary(Substance s) {
        log.trace("starting in generateCodeIfNecessary");
        initializer.getSync();


        if (seqGen != null && s.isPrimaryDefinition()) {
            log.trace("looking for code");
            CodeSequentialGenerator codeSequentialGenerator = seqGen.getSync();
            boolean hasCode = false;
            for (Code c : s.codes) {
                if (c.codeSystem.equals(codeSequentialGenerator.getCodeSystem())) {
                    hasCode = true;
                    break;
                }
            }
            if (!hasCode) {
                try {
                    codeSequentialGenerator.addCode(s);
                    log.trace("Generating new code for substance");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void prePersist(Substance s) {
        generateCodeIfNecessary(s);
    }

    @Override
    public void preUpdate(Substance s) {
        generateCodeIfNecessary(s);
    }

    @Override
    public Class<Substance> getEntityClass() {
        return Substance.class;
    }

    private void addCodeSystemIfNecessary() {
        log.trace("starting addCodeSystemIfNecessary. cvDomain: " + CODE_SYSTEM_VOCABULARY + "; codeSystem: " + codeSystem);
        
        try {
            Optional<GsrsCodeSystemControlledVocabularyDTO> opt = cvApi.findByDomain(CODE_SYSTEM_VOCABULARY);

            boolean addNew = true;
            if (opt.isPresent()) {
                log.trace("CV_DOMAIN found");
                for (CodeSystemTermDTO term : (opt.get()).getTerms()) {
                    if (term.getValue().equals(codeSystem)) {
                        addNew = false;
                        break;
                    }
                }
                log.trace("addNew: " + addNew);
                if (addNew) {
                    List<CodeSystemTermDTO> list = opt.get().getTerms().stream()
                            .map(t -> (CodeSystemTermDTO) t)
                            .collect(Collectors.toList());
                    list.add(CodeSystemTermDTO.builder()
                            .display(codeSystem)
                            .value(codeSystem)
                            .hidden(true)
                            .build());

                    opt.get().setTerms(list);

                    cvApi.update(opt.get());
                    log.trace("saved updated CV");
                }
            }
            else {
                log.error("no code system CV found!");
                //create it ?
                GsrsCodeSystemControlledVocabularyDTO dto =  GsrsCodeSystemControlledVocabularyDTO.builder()
                        .domain(CODE_SYSTEM_VOCABULARY)

                        .terms(Arrays.asList(  CodeSystemTermDTO.builder()
                                .display(codeSystem)
                                .value(codeSystem)
                                .hidden(true)
                                .build()))
                        .build();
                cvApi.create(dto);
                log.trace("created CV");
            }

        } catch (IOException e) {
            log.error("Error updating GSRS vocabulary: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
