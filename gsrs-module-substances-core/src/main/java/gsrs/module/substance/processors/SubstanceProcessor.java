package gsrs.module.substance.processors;

import gsrs.module.substance.repository.RelationshipRepository;
import gsrs.module.substance.repository.SubstanceRepository;
import gsrs.EntityPersistAdapter;
import ix.core.EntityProcessor;

import ix.ginas.models.v1.Relationship;
import ix.ginas.models.v1.Substance;
import ix.ginas.models.v1.Substance.SubstanceDefinitionType;
import ix.ginas.models.v1.SubstanceReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;

/**
 * This Substance Processor makes the following changes when a Substance is saved:
 *
 * <ol>
 *     <li>If this Substance is an Alternate Definition: replace the reference to its PRIMARY definition
 *          to make sure it's up to date.</li>
 *
 *
 * </ol>
 *
 * If the Substance is newly created/ just inserted, then
 * look for dangling Relationships where previously loaded substances refer to this new substance
 * and if it finds any, add the corresponding inverse relationship.  This is probably
 * only done during partial BATCH loads.
 *
 */
@Slf4j
public class SubstanceProcessor implements EntityProcessor<Substance> {


    @Autowired
    private RelationshipRepository relationshipRepository;

    @Autowired
    private EntityPersistAdapter entityPersistAdapter;

    @Autowired
    private SubstanceRepository substanceRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public Class<Substance> getEntityClass() {
        return Substance.class;
    }


    private void addWaitingRelationships(Substance obj){

        List<Relationship> refrel = relationshipRepository.findByRelatedSubstance_Refuuid(obj.getOrGenerateUUID().toString());

        for(Relationship r:refrel){
            Substance owner = r.fetchOwner();
            log.debug("finding inverse owner simple method:" + owner);
            if(owner==null) {
                owner= substanceRepository.findByRelationships_Uuid(r.uuid);
                log.debug("finding inverse owner direct lookup method:" + owner);
            }
            if(owner==null) {
                owner= obj.relationships
                   .stream()
                   .filter(rr->{
                       boolean keep=Objects.equals(rr.originatorUuid,r.originatorUuid);
                       
                       return keep;
                       })
                   .findFirst()
                   .map(rr->{
                       return substanceRepository.findBySubstanceReference(rr.relatedSubstance);
                   })
                   .orElse(null);

                log.debug("finding inverse owner convoluted method:" + owner);
            }
            eventPublisher.publishEvent(
                    TryToCreateInverseRelationshipEvent.builder()
                            .creationMode(TryToCreateInverseRelationshipEvent.CreationMode.CREATE_IF_MISSING)
                            .originatorSubstance(owner.uuid)
                            .toSubstance(owner.uuid)
                            .fromSubstance(obj.uuid)
                            .relationshipIdToInvert(r.uuid)
                            .build()
                    );

        }
    }


    @Override
    @Transactional
    public void prePersist(final Substance s) {
        savingSubstance(s, true);
    }

    private void savingSubstance(final Substance s, boolean newInsert) {



        log.debug("Persisting substance:" + s);
        if (s.isAlternativeDefinition()) {

            log.debug("It's alternative");
            //If it's alternative, find the primary substance (there should only be 1, but this returns a list anyway)
            List<Substance> realPrimarysubs=substanceRepository.findSubstancesWithAlternativeDefinition(s);
            log.debug("Got some relationships:" + realPrimarysubs.size());
            Set<String> oldprimary = new HashSet<String>();
            for(Substance pri:realPrimarysubs){
                oldprimary.add(pri.getUuid().toString());
            }


            SubstanceReference sr = s.getPrimaryDefinitionReference();
            if (sr != null) {

                log.debug("Enforcing bidirectional relationship");
                //remove old references
                for(final Substance oldPri: realPrimarysubs){
                    if(oldPri ==null){
                        continue;
                    }
                    log.debug("Removing stale bidirectional relationships");


                    entityPersistAdapter.performChangeOn(oldPri, obj->{
                        List<Relationship> related=oldPri.removeAlternativeSubstanceDefinitionRelationship(s);
                        for(Relationship r:related){
                            relationshipRepository.delete(r);
                        }
                        oldPri.forceUpdate();
                        return Optional.of(obj);
                    }
                            );


                }
                log.debug("Expanding reference");
                Substance subPrimary=null;
                try{
                    subPrimary = substanceRepository.findBySubstanceReference(sr);
                }catch(Exception e){
                    e.printStackTrace();
                }

                if (subPrimary != null) {
                    log.debug("Got parent sub, which is:" + subPrimary.getName());
                    if (subPrimary.definitionType == SubstanceDefinitionType.PRIMARY) {

                        log.debug("Going to save");

                        entityPersistAdapter.performChangeOn(subPrimary, obj -> {
                            if (!obj.addAlternativeSubstanceDefinitionRelationship(s)) {
                                log.info("Saving alt definition, now has:"
                                        + obj.getAlternativeDefinitionReferences().size());
                            }
                            obj.forceUpdate();
                            return Optional.of(obj);
                        });

                    }
                }

            }else{
                log.error("Persist error. Alternative definition has no primary relationship");
            }
        }

        if(newInsert) {
            //depending on how this substance was created
            //it might have been from copying and pasting old json
            //of an already existing substance
            //which might have the change reason set so force it to be null for new inserts
            s.changeReason=null;

            addWaitingRelationships(s);
    }
    }



    @Override
    public void preUpdate(Substance obj) {
        savingSubstance(obj, false);
    }







}
