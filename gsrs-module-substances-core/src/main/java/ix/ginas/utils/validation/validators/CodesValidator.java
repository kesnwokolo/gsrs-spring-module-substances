package ix.ginas.utils.validation.validators;


import gsrs.module.substance.repository.ReferenceRepository;
import gsrs.module.substance.repository.SubstanceRepository;
import ix.core.models.Keyword;
import ix.core.util.LogUtil;
import ix.core.validator.GinasProcessingMessage;
import ix.core.validator.ValidatorCallback;
import ix.ginas.models.v1.Code;
import ix.ginas.models.v1.Reference;
import ix.ginas.models.v1.Substance;
import ix.ginas.utils.validation.AbstractValidatorPlugin;
import ix.ginas.utils.validation.ValidationUtils;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by katzelda on 5/14/18.
 */
public class CodesValidator extends AbstractValidatorPlugin<Substance> {

    private Set<String> singletonCodeSystems;

    @Autowired
    private ReferenceRepository referenceRepository;

    @Autowired
    private SubstanceRepository substanceRepository;


    @Override
    public void validate(Substance s, Substance objold, ValidatorCallback callback) {

        Iterator<Code> codesIter = s.codes.iterator();
        while(codesIter.hasNext()){
            Code cd = codesIter.next();
            if (cd == null) {
                GinasProcessingMessage mes = GinasProcessingMessage
                        .WARNING_MESSAGE("Null code objects are not allowed")
                        .appliableChange(true);
                callback.addMessage(mes, ()->codesIter.remove());
                continue;
            }

                if (ValidationUtils.isEffectivelyNull(cd.code)) {
                    GinasProcessingMessage mes = GinasProcessingMessage
                            .ERROR_MESSAGE(
                                    "'Code' should not be null in code objects")
                            .appliableChange(true);
                    callback.addMessage(mes, ()-> cd.code="<no code>");

                }else if (!(cd.code+"").trim().equals(cd.code+"")) {
                    GinasProcessingMessage mes = GinasProcessingMessage
                            .WARNING_MESSAGE(
                                    "'Code' '" + cd.code + "' should not have trailing or leading whitespace. Code will be trimmed to '" + cd.code.trim() + "'")
                            .appliableChange(true);
                    callback.addMessage(mes, ()-> cd.code=(cd.code+"").trim());

                }


            if (ValidationUtils.isEffectivelyNull(cd.codeSystem)) {
                    GinasProcessingMessage mes = GinasProcessingMessage
                            .ERROR_MESSAGE(
                                    "'Code System' should not be null in code objects")
                            .appliableChange(true);
                    callback.addMessage(mes, ()->cd.codeSystem="<no system>");

                }

                if (ValidationUtils.isEffectivelyNull(cd.type)) {
                    GinasProcessingMessage mes = GinasProcessingMessage
                            .WARNING_MESSAGE(
                                    "Must specify a code type for each name. Defaults to \"PRIMARY\" (PRIMARY)")
                            .appliableChange(true);
                    callback.addMessage(mes, ()-> cd.type="PRIMARY");

                }


            if (!ValidationUtils.validateReference(s, cd, callback, ValidationUtils.ReferenceAction.ALLOW, referenceRepository)) {
                return;
            }

        }



        for (Code cd : s.codes) {
            try {
                if( containsLeadingTrailingSpaces(cd.comments) ) {
                        GinasProcessingMessage mes = GinasProcessingMessage
                                .WARNING_MESSAGE(
                                        "Code '"
                                                + cd.code
                                                + "'[" +cd.codeSystem
                                                + "] "
                                        + "code text: " +  cd.comments  +" contains one or more leading/trailing blanks that will be removed")
                                .appliableChange(true);
                        callback.addMessage(mes);
                }
                if( singletonCodeSystems != null && !singletonCodeSystems.contains(cd.codeSystem)) {
                    LogUtil.trace( ()->String.format("skipping code of system %s", cd.codeSystem));
                    continue;
                }
                List<SubstanceRepository.SubstanceSummary> sr = substanceRepository.findByCodes_CodeAndCodes_CodeSystem(cd.code, cd.codeSystem);

                if (sr != null && !sr.isEmpty()) {
                    //TODO we only check the first hit?
                    //would be nice to say instead of possible duplicate hit say we got X hits
                    SubstanceRepository.SubstanceSummary s2 = sr.iterator().next();

                    if (s2.getUuid() != null && !s2.getUuid().equals(s.getUuid())) {
                        GinasProcessingMessage mes = GinasProcessingMessage
                                .WARNING_MESSAGE(
                                        "Code '"
                                                + cd.code
                                                + "'[" +cd.codeSystem
                                                + "] collides (possible duplicate) with existing code & codeSystem for substance:")
//                               TODO katelda Feb 2021 : add link support back!
                                . addLink(ValidationUtils.createSubstanceLink(s2.toSubstanceReference()))
                                ;
                        callback.addMessage(mes);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Set<String> getSingletonCodeSystems() {
        return singletonCodeSystems;
    }

    public void setSingletonCodeSystems(Set<String> singletonCodeSystems) {
        this.singletonCodeSystems = singletonCodeSystems;
    }

    public static boolean containsLeadingTrailingSpaces(String comment) {
        if( comment==null || comment.length()==0){
            return false;
        }
        if( !comment.equals(comment.trim())){
            return true;
        }
        String[] lines = comment.split("\\|");
        for(String line : lines) {
            if( line!=null && line.length()>0 && !line.equals(line.trim())) {
                return true;
            }
        }
        return false;
    }
    
    public static String removeLeadingTrailingSpaces(String comment) {
        if( comment==null || comment.length()==0){
            return comment;
        }
        List<String> cleanLines = new ArrayList<>();
        String[] lines = comment.split("\\|");
        for(String line : lines) {
            cleanLines.add(line.trim());
        }
        return StringUtils.join(cleanLines);
    }
}
