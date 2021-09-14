package example.substance.datasearch;

import com.fasterxml.jackson.databind.JsonNode;
import example.substance.AbstractSubstanceJpaFullStackEntityTest;
import gsrs.module.substance.controllers.SubstanceLegacySearchService;
import gsrs.module.substance.definitional.DefinitionalElements;
import gsrs.module.substance.indexers.SubstanceDefinitionalHashIndexer;
import gsrs.module.substance.services.DefinitionalElementFactory;
import gsrs.service.GsrsEntityService;
import gsrs.springUtils.AutowireHelper;
import gsrs.startertests.TestGsrsValidatorFactory;
import gsrs.startertests.TestIndexValueMakerFactory;
import gsrs.validator.DefaultValidatorConfig;
import gsrs.validator.ValidatorConfig;
import ix.core.chem.StructureProcessor;
import ix.core.search.SearchRequest;
import ix.core.search.SearchResult;
import ix.core.util.EntityUtils.EntityWrapper;
import ix.ginas.modelBuilders.ChemicalSubstanceBuilder;
import ix.ginas.modelBuilders.SubstanceBuilder;
import ix.ginas.models.v1.ChemicalSubstance;
import ix.ginas.models.v1.Substance;
import ix.ginas.models.v1.Substance.SubstanceClass;
import ix.ginas.utils.validation.validators.ChemicalValidator;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.test.context.support.WithMockUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author mitch
 */
//Changed base class from AbstractSubstanceJpaFullStackEntityTest to AbstractSubstanceJpaEntityTest
// 16 July based on recommendation from Danny K.
// 7 August Tyler Peryea refactored this class to be cleaner and more DRY-adherent 
@WithMockUser(username = "admin", roles = "Admin")
@Slf4j
public class DataSearch18Tests extends AbstractSubstanceJpaFullStackEntityTest {

    @Autowired
    private SubstanceLegacySearchService searchService;

    @Autowired
    private DefinitionalElementFactory definitionalElementFactory;

    @Autowired
    private TestIndexValueMakerFactory testIndexValueMakerFactory;

    @Autowired
    StructureProcessor structureProcessor;

    @Autowired
    private TestGsrsValidatorFactory factory;

    private String fileName = "rep18.gsrs";

    @BeforeEach
    public void clearIndexers() throws IOException {
        SubstanceDefinitionalHashIndexer hashIndexer = new SubstanceDefinitionalHashIndexer();
        AutowireHelper.getInstance().autowire(hashIndexer);
        testIndexValueMakerFactory.addIndexValueMaker(hashIndexer);
        {
            ValidatorConfig config = new DefaultValidatorConfig();
            config.setValidatorClass(ChemicalValidator.class);
            config.setNewObjClass(ChemicalSubstance.class);
            factory.addValidator("substances", config);
        }

        File dataFile = new ClassPathResource(fileName).getFile();
        loadGsrsFile(dataFile);
    }

    @Test
    public void testSearchByName() {

        String name1 = "THIOFLAVIN S2";
        String idForName = "e92bc4ad-250a-4eef-8cd7-0b0b1e3b6cf0";

        SearchRequest request = new SearchRequest.Builder()
                .kind(Substance.class)
                .fdim(0)
                .query("root_names_name:\"" + name1 + "\"")
                .top(Integer.MAX_VALUE)
                .build();

        List<Substance> substances = getSearchList(request);

        /*
        as of 21 July 2021, I am puzzled by the inability to get a List<String> directly from
        transactionSearch.execute.
        the IDE accepts the code like
            List<String> ids = transactionSearch.execute.....
                with a lambda that returns a List<String>
        but there's a runtime class cast exception.
         */
        System.out.println("substances size: " + substances.size());
        String actualId = substances.stream()
                .map(s -> s.uuid.toString())
                .findFirst().get();

        assertEquals(idForName, actualId);
    }

    @Test
    public void testFacetRestrictChemicals() {
        SearchRequest sreq = new SearchRequest.Builder()
                .addFacet("Substance Class", "chemical")
                .kind(Substance.class)
                .build();

        List<Substance> matches = getSearchList(sreq);
        int chems = 0;
        int others = 0;

        for (Substance s : matches) {
            if (s.substanceClass.equals(SubstanceClass.chemical)) {
                chems++;
            }
            else {
                others++;
            }
        }
        assertEquals(0, others, "Expect only chemicals to come back on faceted search for chemicals");
        assertEquals(9, chems, "Expect 9 chemicals to come back on faceted search for chemicals");
    }

    @Test
    public void testSortMwt() {
        SearchRequest sreq = new SearchRequest.Builder()
                .addFacet("Substance Class", "chemical")
                .addOrder("^root_structure_mwt")
                .kind(Substance.class)
                .build();

        List<Substance> matches = getSearchList(sreq);
        List<Substance> sorted = matches.stream()
                .map(s -> (ChemicalSubstance) s)
                .sorted(Comparator.comparing(cs -> cs.getStructure().mwt))
                .collect(Collectors.toList());

        for (int i = 0; i < matches.size(); i++) {
            Substance r1 = matches.get(i);
            Substance e1 = sorted.get(i);
            assertEquals(e1.uuid, r1.uuid, "Expected chemicals sorted by molecular weight, but were returned in the wrong order");
        }
    }

    @Test
    public void testSearchByApprovalID() {
        String approvalID1 = "D733ET3F9O";
        String idForName = "deb33005-e87e-4e7f-9704-d5b4c80d3023";

        SearchRequest request = new SearchRequest.Builder()
                .kind(Substance.class)
                .fdim(0)
                .query("root_approvalID:\"" + approvalID1 + "\"")
                .top(Integer.MAX_VALUE)
                .build();
        List<Substance> substances = getSearchList(request);

        System.out.println("substances size: " + substances.size());
        String actualId = substances.stream()
                .map(s -> s.uuid.toString())
                .findFirst().get();

        assertEquals(idForName, actualId);
    }

    @Test
    public void testSearchByCodeSystem() {
        String codeSystem1 = "DRUG CENTRAL";
        List<String> expectedIds = Arrays.asList("302cedcc-895f-421c-acf4-1348bbdb31f4", "79dbcc59-e887-40d1-a0e3-074379b755e4",
                "deb33005-e87e-4e7f-9704-d5b4c80d3023", "5b611b0d-b798-45ed-ba02-6f0a2f85986b",
                "306d24b9-a6b8-4091-8024-02f9ec24b705", "90e9191d-1a81-4a53-b7ee-560bf9e68109");
        Collections.sort(expectedIds);//use default sort order

        SearchRequest request = new SearchRequest.Builder()
                .kind(Substance.class)
                .fdim(0)
                .query("root_codes_codeSystem:\"" + codeSystem1 + "\"")
                .top(Integer.MAX_VALUE)
                .build();
        List<Substance> substances = getSearchList(request);

        System.out.println("substances size: " + substances.size());
        List<String> actualIds = substances.stream()
                .map(s -> s.uuid.toString())
                .sorted() //use default sort order
                .collect(Collectors.toList());

        assertEquals(actualIds, expectedIds);
    }

    @Test
    public void testSearchByCodeSystemAndClass() {
        String codeSystem1 = "DRUG BANK";
        String substanceClass = "protein";
        List<String> expectedIds = Arrays.asList("044e6d9c-37c0-42ac-848e-2e41937216b1", "deb33005-e87e-4e7f-9704-d5b4c80d3023");
        Collections.sort(expectedIds);//use default sort order
        SearchRequest request = new SearchRequest.Builder()
                .kind(Substance.class)
                .fdim(0)
                .query("root_codes_codeSystem:\"" + codeSystem1 + "\"  AND root_substanceClass:\""
                        + substanceClass + "\"")
                .top(Integer.MAX_VALUE)
                .build();
        List<Substance> substances = getSearchList(request);

        System.out.println("substances size: " + substances.size());
        List<String> actualIds = substances.stream()
                .map(s -> s.uuid.toString())
                .sorted() //use default sort order
                .collect(Collectors.toList());
        assertEquals(actualIds, expectedIds);
    }

    @Test
    public void testSearchForChemicals() {
        String substanceClass = "chemical";
        int expectedNumber = 9;
        SearchRequest request = new SearchRequest.Builder()
                .kind(Substance.class)
                .query("root_substanceClass:\"" + substanceClass + "\"")
                .build();
        List<Substance> substances = getSearchList(request);

        substances.forEach(s -> System.out.println("substance with ID " + s.uuid));
        assertEquals(expectedNumber, substances.size());
    }

    @Test
    public void testDuplicates() {
        Substance chemical = getSampleChemicalFromFile();
        chemical.uuid = UUID.randomUUID();

        List<Substance> matches = findFullDefinitionalDuplicateCandidates(chemical);
        assertTrue(matches.size() > 0, "must find some duplicates");
    }

    @Test
    public void testSearchAfterRelationshipCreation() {
        //step 1: look for substance:
        String approvalID1 = "D733ET3F9O";
        String idForName = "deb33005-e87e-4e7f-9704-d5b4c80d3023";

        SearchRequest request = new SearchRequest.Builder()
                .kind(Substance.class)
                .fdim(0)
                .query("root_approvalID:\"" + approvalID1 + "\"")
                .top(Integer.MAX_VALUE)
                .build();
        List<Substance> substances = getSearchList(request);

        System.out.println("substances size: " + substances.size());
        String actualId = substances.stream()
                .map(s -> s.uuid.toString())
                .findFirst().get();

        assertEquals(idForName, actualId);
        Substance baseSubstance = this.substanceRepository.getOne(UUID.fromString(idForName));

        //step 2: retrieve a second substance, add a relationship to the first substance
        String idToLookup = "1cf410f9-3eeb-41ed-ab69-eeb5076901e5";
        String relationshipType = "PARENT->SALT/SOLVATE";
        Substance toModify = getSubstanceFromUUID(idToLookup);
        //this.substanceRepository.getOne(UUID.fromString(idToLookup));
        Assert.assertNotNull(toModify);
        TransactionTemplate transactionMod = new TransactionTemplate(transactionManager);
        transactionMod.executeWithoutResult(a -> {
            JsonNode node = toModify.toBuilder()
                    .addRelationshipTo(baseSubstance, relationshipType)
                    .buildJson();
            try {
                GsrsEntityService.UpdateResult<Substance> result = this.substanceEntityService.updateEntity(node);
                assertEquals(result.getStatus(), GsrsEntityService.UpdateResult.STATUS.UPDATED);
            } catch (Exception ex) {
                log.error("Error updating substance: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        Substance modified = toModify.toBuilder()
                .addRelationshipTo(baseSubstance, relationshipType)
                .build();
        this.substanceRepository.save(modified);

        //step 3: look up the first substance again
        List<Substance> substancesAfer = getSearchList(request);
        assertEquals(substances.size(), substancesAfer.size());

        //step 3: look up the second substance
        String approvalID2 = "E89JCB6A9Q";
        SearchRequest request2 = new SearchRequest.Builder()
                .kind(Substance.class)
                .fdim(0)
                .query("root_approvalID:\"" + approvalID2 + "\"")
                .top(Integer.MAX_VALUE)
                .build();
        List<Substance> substances2 = getSearchList(request2);
        assertEquals(1, substances2.size());
    }

    public List<Substance> findFullDefinitionalDuplicateCandidates(Substance substance) {
        List<Substance> candidates = new ArrayList<>();
        try {
            DefinitionalElements newDefinitionalElements = definitionalElementFactory.computeDefinitionalElementsFor(substance);
            int layer = newDefinitionalElements.getDefinitionalHashLayers().size() - 1; // hashes.size()-1;
            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "handling layer: " + (layer + 1));
            String searchItem = "root_definitional_hash_layer_" + (layer + 1) + ":"
                    + newDefinitionalElements.getDefinitionalHashLayers().get(layer);
            System.out.println("in findFullDefinitionalDuplicateCandidates, searchItem: " + searchItem);
            Logger.getLogger(this.getClass().getName()).log(Level.FINE, "layer query: " + searchItem);
            SearchRequest request = new SearchRequest.Builder()
                    .kind(Substance.class)
                    .query(searchItem)
                    .build();
            candidates = getSearchList(request);

            candidates.stream()
                    .flatMap(ss -> ss.names.stream())
                    .map(n -> n.name)
                    .forEach(n -> System.out.println(n));
        } catch (Exception ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error running query", ex);
        }
        return candidates;
    }

    /**
     * Return a list of substances based on the {@link SearchRequest}. This
     * takes care of some tricky transaction issues.
     *
     * @param sr
     * @return
     */
    private List<Substance> getSearchList(SearchRequest sr) {
        TransactionTemplate transactionSearch = new TransactionTemplate(transactionManager);
        List<Substance> substances = transactionSearch.execute(ts -> {
            try {
                SearchResult sresult = searchService.search(sr.getQuery(), sr.getOptions());
                List<Substance> first = sresult.getMatches();
                return first.stream()
                        //force fetching
                        .peek(ss -> EntityWrapper.of(ss).toInternalJson())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException(e);

            }
        });
        return substances;
    }

    @Transactional
    private Substance getSubstanceFromUUID(String uuid) {
        TransactionTemplate transactionSearch = new TransactionTemplate(transactionManager);
        Substance substance = transactionSearch.execute(ts -> {
            try {
                return this.substanceRepository.getOne(UUID.fromString(uuid));

            } catch (Exception e) {
                throw new RuntimeException(e);

            }
        });
        return substance;
    }

    private Substance getSampleChemicalFromFile() {
        try {
            File chemicalFile = new ClassPathResource(fileName).getFile();
            JsonNode json = yieldSubstancesFromGsrsFile(chemicalFile, Substance.SubstanceClass.chemical)
                    .stream().findFirst().get();
            ChemicalSubstanceBuilder builder = SubstanceBuilder.from(json);

            ChemicalSubstance s = builder.build();
            ChemicalValidator chemicalValidator = new ChemicalValidator();
            chemicalValidator.setStructureProcessor(structureProcessor);
            chemicalValidator.validate(s, null);

            return s;
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
