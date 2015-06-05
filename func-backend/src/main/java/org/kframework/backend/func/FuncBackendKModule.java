package org.kframework.backend.func;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import org.kframework.Rewriter;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.main.AbstractKModule;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author: Remy Goldschmidt
 */
public class FuncBackendKModule extends AbstractKModule {

    @Override
    public List<Module> getKompileModules() {
        return Collections.singletonList(new AbstractModule() {
            @Override
            protected void configure() {

                MapBinder<String, Consumer<CompiledDefinition>> mapBinder = MapBinder.newMapBinder(
                        binder(), TypeLiteral.get(String.class), new TypeLiteral<Consumer<CompiledDefinition>>() {});
                mapBinder.addBinding("func").to(FuncBackend.class);
            }
        });
    }

    @Override
    public List<Module> getDefinitionSpecificKRunModules() {
        return Collections.singletonList(new AbstractModule() {
            @Override
            protected void configure() {

                MapBinder<String, Function<org.kframework.definition.Module, Rewriter>> rewriterBinder = MapBinder.newMapBinder(
                        binder(), TypeLiteral.get(String.class), new TypeLiteral<Function<org.kframework.definition.Module, Rewriter>>() {
                        });
                rewriterBinder.addBinding("func").to(FuncRewriter.class);
            }
        });
    }
}
