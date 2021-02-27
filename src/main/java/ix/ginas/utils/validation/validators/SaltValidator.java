package ix.ginas.utils.validation.validators;

import ix.core.validator.GinasProcessingMessage;
import ix.core.validator.ValidatorCallback;
import ix.ginas.models.v1.Amount;
import ix.ginas.models.v1.ChemicalSubstance;
import ix.ginas.models.v1.Moiety;
import ix.ginas.models.v1.Substance;
import ix.ginas.utils.validation.ValidationUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Mitch Miller
 */
@Slf4j
public class SaltValidator extends AbstractValidatorPlugin<Substance>
{

	public SaltValidator(){
		log.trace("starting in SaltValidator.ctor");
	}
					
	@Override
	public void validate(Substance substanceORIG, Substance oldSubstanceORIG, ValidatorCallback callback) {
		if(!(substanceORIG instanceof ChemicalSubstance)){
			return;
		}
		ChemicalSubstance substance = (ChemicalSubstance)substanceORIG;
		
		log.trace("starting in SaltValidator.validate");
		//is this a salt?
		log.trace("starting in SaltValidator.validate. substance.moieties.size() "+ substance.moieties.size());
		if (substance.moieties.size() > 1) {
			List<String> smilesWithoutMatch = new ArrayList();
			List<String> smilesWithPartialMatch = new ArrayList();
			
			for (Moiety moiety : substance.moieties) {
				log.trace("Looking up moiety with SMILES " + moiety.structure.smiles);
				
				ChemicalSubstance moietyChemical = new ChemicalSubstance();
				moietyChemical.structure = moiety.structure;
				//clone the moiety and set fixed values so that we avoid flagging a moiety as not matching
				Moiety clone = new Moiety();
				clone.structure =moiety.structure;
				Amount cloneAmount = new Amount();
				cloneAmount.average=1.0d;
				cloneAmount.units = "MOL RATIO";
				cloneAmount.type = "MOL RATIO";
				clone.setCountAmount(cloneAmount);
				moietyChemical.moieties.add(clone);
				
				List<Substance> layer1Matches= ValidationUtils.findDefinitionaLayer1lDuplicateCandidates(moietyChemical);
				log.trace("(SaltValidator) total layer1 matches: " + layer1Matches.size());
				
				//skip the look-up of full matches when there are no layer1 matches
				List<Substance> fullMatches= (layer1Matches.isEmpty())? new ArrayList() :
								ValidationUtils.findFullDefinitionalDuplicateCandidates(moietyChemical);
				log.trace("(SaltValidator) total full matches: " + fullMatches.size());
				
				if( fullMatches.isEmpty()) {
					if( layer1Matches.isEmpty()){
						smilesWithoutMatch.add(moiety.structure.smiles);
					} else {
						smilesWithPartialMatch.add(moiety.structure.smiles);
					}
				}
			}

			if (!smilesWithoutMatch.isEmpty()) {
				smilesWithoutMatch.forEach(s->{ 
					callback.addMessage(GinasProcessingMessage.WARNING_MESSAGE("Each fragment should be present as a separate record in the database. Please register: " + s) );
				});
			}
			if( !smilesWithPartialMatch.isEmpty()){
				smilesWithPartialMatch.forEach(s->{
					callback.addMessage(GinasProcessingMessage.WARNING_MESSAGE("This fragment is present as a separate record in the database but in a different form. Please register: "
									+ s + " as an individual substance") );
					
				});
			}
			
			if(smilesWithoutMatch.isEmpty() && smilesWithPartialMatch.isEmpty()) {
				log.trace("all moieties are present in the database");
			}
		}
	}
}
