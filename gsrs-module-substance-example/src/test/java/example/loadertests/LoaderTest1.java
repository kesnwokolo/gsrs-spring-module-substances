package example.loadertests;

import example.substance.AbstractSubstanceJpaFullStackEntityTest;
import gov.nih.ncats.structureIndexer.StructureIndexer;
import gov.nih.ncats.structureIndexer.StructureIndexer.Result;
import gsrs.legacy.structureIndexer.StructureIndexerService;
import gsrs.module.substance.SubstanceEntityService;
import gsrs.services.PrincipalServiceImpl;
import ix.ginas.modelBuilders.ChemicalSubstanceBuilder;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 * @author mitch todo: get facets for searches check on structure mapping
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LoaderTest1 extends AbstractSubstanceJpaFullStackEntityTest
{

    @Autowired
    private StructureIndexerService indexer;

    @Autowired
    private SubstanceEntityService substanceEntityService;

    @Autowired
    private PrincipalServiceImpl principalService;

    @BeforeEach
    public void clearIndexers() throws IOException {
        indexer.removeAll();
        principalService.clearCache();
    }

    public LoaderTest1() {
    }

    private int countStructureSearchHits(String structure, String compoundName) throws Exception {
        UUID uuid = UUID.randomUUID();

        if (compoundName != null && compoundName.length() > 0) {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(s -> {
                new ChemicalSubstanceBuilder()
                        .setStructure(structure)
                        .addName(compoundName)
                        .setUUID(uuid)
                        .buildJsonAnd(this::assertCreated);
            });
        }

        StructureIndexer.ResultEnumeration result = indexer.substructure(structure);
        AtomicInteger count = new AtomicInteger(0);
        if (compoundName != null && compoundName.length() > 0) {
            assertTrue(result.hasMoreElements());
        }
        while (result.hasMoreElements()) {
            Result next =result.nextElement();
            System.out.println("ID: " + next.getId() + "; formula: "+ next.getMol().getFormula());
            count.incrementAndGet();
//            Chemical currentMol = r1.getMol();
//            String msg = String.format("hit %d. ID: %s; total atoms: %d, formula: %s", count.incrementAndGet(),
//                    r1.getId(), currentMol.getAtomCount(), currentMol.getFormula());
//            System.out.println(msg);
        }
        return count.get();
    }

    @Test
    @WithMockUser(username = "admin", roles = "Admin")
    public void loadAsAdmin() throws Exception {
        File dataFile = new ClassPathResource("testdumps/rep90.ginas").getFile();
        //String structure = "C1CCCCC1";
        String structure = "c1ccccc1";

        int actualHits = countStructureSearchHits(structure, "benzene");
        assertEquals(1, actualHits);//will find itself
        indexer.removeAll();
        loadGsrsFile(dataFile);
        int actualHits2 = countStructureSearchHits(structure, "benzene");
        int totalExpectedHits = 21;
        assertEquals(totalExpectedHits, actualHits2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "Admin")
    public void loadMultipleFiles() throws Exception {
        File dataFile1 = new ClassPathResource("testdumps/rep90_part1.ginas").getFile();
        File dataFile2 = new ClassPathResource("testdumps/rep90_part2.ginas").getFile();
        loadGsrsFile(dataFile1);
        loadGsrsFile(dataFile2);

        String structure = "c1ccccc1";
        int actualHits = countStructureSearchHits(structure, "benzene");
        int totalExpectedHits = 20;
        assertEquals(totalExpectedHits, actualHits);
    }

    @Test
    @WithMockUser(username = "admin", roles = "Admin")
    public void loadAsciiFile() throws Exception {
        //this input file is unzipped -- in text format
        File dataFile1 = new ClassPathResource("testdumps/rep90.txt").getFile();
        loadGsrsFile(dataFile1);

        String structure = "c1ccccc1";
        int actualHits = countStructureSearchHits(structure, "benzene");
        int totalExpectedHits = 20;
        assertEquals(totalExpectedHits, actualHits);
    }

    @Test
    @WithMockUser(username = "admin", roles = "Admin")
    public void noDataLoadedShouldReturnZeroResults() throws Exception {

        int actualHits = countStructureSearchHits("C1=CC=CC=C1", null);
        assertEquals(0, actualHits);
        //assertEquals(Collections.emptyMap(), results.getAllFacets());
    }
}
