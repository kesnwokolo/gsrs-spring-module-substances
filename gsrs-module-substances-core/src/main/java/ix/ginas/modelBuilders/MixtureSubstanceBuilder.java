package ix.ginas.modelBuilders;

import ix.ginas.models.v1.*;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Created by katzelda on 7/20/18.
 */
public class MixtureSubstanceBuilder extends AbstractSubstanceBuilder<MixtureSubstance, MixtureSubstanceBuilder> {


    public MixtureSubstanceBuilder(Substance copy) {
        super(copy);
        if(copy instanceof MixtureSubstance){
            Mixture mix = ((MixtureSubstance)copy).mixture;
            if(mix !=null){
                setMixture(mix);
            }
        }
    }

    protected <S extends Substance> MixtureSubstanceBuilder(AbstractSubstanceBuilder<S,?> builder){
        this.andThen = (s)-> (MixtureSubstance) builder.andThen.apply((S) s);
    }
    public MixtureSubstanceBuilder() {
        super();
    }

    @Override
    public Supplier<MixtureSubstance> getSupplier() {
        return () ->{
            MixtureSubstance s = new MixtureSubstance();
            s.mixture = new Mixture();
            return s;
        };
    }

    @Override
    protected Substance.SubstanceClass getSubstanceClass() {
        return Substance.SubstanceClass.mixture;
    }

    @Override
    protected MixtureSubstanceBuilder getThis() {
        return this;
    }

    public MixtureSubstanceBuilder setMixture(Mixture mixture){
        andThen( s ->{
            s.mixture = mixture;
        });
        return getThis();
    }

    public MixtureSubstanceBuilder addComponent(Component c){
        return andThen( s-> {
            s.mixture.components.add(Objects.requireNonNull(c));
        });

    }

    public MixtureSubstanceBuilder addComponents(String type, Substance... refs){
        return andThen( s-> {
            for (Substance ref : refs) {
                Component c = new Component();
                c.type = type;
                c.substance = ref.asSubstanceReference();
                s.mixture.components.add(c);
            }
            Reference ref = AbstractSubstanceBuilder.createNewPublicDomainRef();
            s.mixture.addReference(ref, s);
        });
    }
}
