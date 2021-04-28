package gsrs.module.substance.autoconfigure;

import gsrs.EnableGsrsAkka;
import gsrs.EnableGsrsApi;
import gsrs.EnableGsrsJpaEntities;
import gsrs.GsrsFactoryConfiguration;
import gsrs.cache.GsrsCache;
import gsrs.controller.EditEntityService;
import gsrs.module.substance.SubstanceCoreConfiguration;
import gsrs.module.substance.SubstanceEntityService;
import gsrs.module.substance.SubstanceEntityServiceImpl;
import gsrs.module.substance.controllers.*;
import gsrs.module.substance.repository.*;
import gsrs.module.substance.services.*;
import gsrs.service.PayloadService;
import ix.seqaln.service.SequenceIndexerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties
//@EnableEurekaClient
@EnableGsrsAkka
@EnableGsrsJpaEntities
@EnableGsrsApi
@Import({SubstanceCoreConfiguration.class
})
//@Import({StructureProcessingConfiguration.class, SubstanceEntityService.class,
//        NucleicAcidSubstanceRepository.class, ComponentRepository.class,
//        NameRepository.class, ProteinSubstanceRepository.class, ReferenceRepository.class,
//         StructureRepository.class, SubunitRepository.class, ValueRepository.class,
//        ETagRepository.class
//})
public class GsrsSubstanceModuleAutoConfiguration {

    @Autowired
    private SequenceIndexerService sequenceIndexerService;

    @Autowired
    private GsrsCache ixCache;

    @Autowired
    PayloadService payloadService;
    @Autowired
    private ProteinSubstanceRepository proteinSubstanceRepository;
    @Autowired
    private NucleicAcidSubstanceRepository nucleicAcidSubstanceRepository;

    @Primary
    @Bean
    @ConfigurationProperties("gsrs")
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GsrsFactoryConfiguration gsrsFactoryConfiguration(){
        return new GsrsFactoryConfiguration();
    }

    @Bean("SubstanceSequenceSearchService")
    @ConditionalOnMissingBean(SubstanceSequenceSearchService.class)
    public SubstanceSequenceSearchService getSequenceSearchService(){
        return new LegacySubstanceSequenceSearchService(sequenceIndexerService, ixCache,payloadService,
                proteinSubstanceRepository, nucleicAcidSubstanceRepository);
    }

    //let's try creating the beans for our controllers and services

    @Bean
    @ConditionalOnMissingBean(SubstanceEntityService.class)
    public SubstanceEntityService substanceEntityService(){
        return new SubstanceEntityServiceImpl();
    }
    @Bean
    @ConditionalOnMissingBean(EditEntityService.class)
    public EditEntityService editEntityService(){
        return new EditEntityService();
    }
    @Bean
    @ConditionalOnMissingBean(SubstanceController.class)
    public SubstanceController substanceController(){
        return new SubstanceController();
    }
    @Bean
    @ConditionalOnMissingBean(NameEntityService.class)
    public NameEntityService nameEntityService(){
        return new NameEntityService();
    }
    @Bean
    @ConditionalOnMissingBean(CodeEntityService.class)
    public CodeEntityService codeEntityService(){
        return new CodeEntityService();
    }
    @Bean
    @ConditionalOnMissingBean(ReferenceEntityService.class)
    public ReferenceEntityService referenceEntityService(){
        return new ReferenceEntityService();
    }
}