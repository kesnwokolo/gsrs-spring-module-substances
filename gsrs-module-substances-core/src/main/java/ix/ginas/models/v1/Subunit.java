package ix.ginas.models.v1;


import com.fasterxml.jackson.annotation.JsonIgnore;
import ix.core.SingleParent;
import ix.core.models.Indexable;
import ix.core.models.SequenceEntity;
import ix.ginas.models.GinasAccessReferenceControlled;
import ix.ginas.models.GinasCommonData;
import ix.ginas.models.GinasCommonSubData;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Type;

@SuppressWarnings("serial")
@Entity
@Table(name="ix_ginas_subunit")
@SingleParent
public class Subunit extends GinasCommonSubData implements SequenceEntity {
    @Lob
    @Type(type="org.hibernate.type.TextType")
    @Basic(fetch= FetchType.EAGER)
    @Indexable(sequence=true)
    public String sequence;

    public Integer subunitIndex;
    /**
     * parent object for this subunit
     * mostly used so we know what kind of type
     * this subunit is by walking up the tree
     * to inspect its parent.  See {@link Protein#adoptChildSubunits()}
     */
    @Transient
    @JsonIgnore
    private GinasCommonSubData parent;

    public Subunit () {}
    



    public int getLength(){
    	if(sequence!=null){
    		return sequence.length();
    	}else{
    		return 0;
    	}
    }
    
    @JsonIgnore
    public GinasCommonSubData getParent(){
    	return parent;

    }

    @JsonIgnore
    public void setParent(GinasCommonSubData p){
    	this.parent=p;
    }


//    public List<char[]> getCharArr(){
//    	List<char[]> returnArr = new ArrayList<char[]>();
//    	int index = 0;
//    	while (index < sequence.length()) {
//    	    returnArr.add(sequence.substring(index, Math.min(index + 10,sequence.length())).toCharArray());
//    	    index += 10;
//    	}
//		return returnArr;
//    }
//    public char[] subSeq(int start, int stop){
//    	char[] sub = new char[10];
//    	sequence.getChars(start, stop, sub, 0);
//    	return sub;
//    }

    @Override
   	@JsonIgnore
   	public List<GinasAccessReferenceControlled> getAllChildrenCapableOfHavingReferences() {
   		List<GinasAccessReferenceControlled> temp = new ArrayList<>();


   		return temp;
   	}

    @Override
    public SequenceType computeSequenceType() {
        GinasCommonData p = getParent();
        if(p instanceof Protein){
            return SequenceType.PROTEIN;
        }
        if(p instanceof NucleicAcid){
            return SequenceType.NUCLEIC_ACID;
        }
        return SequenceType.UNKNOWN;
    }
}
