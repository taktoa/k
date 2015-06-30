// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Binder;
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
 * Dependency injection for the functional backend
 *
 * @author Remy Goldschmidt
 */
public class FuncBackendKModule extends AbstractKModule {
    @Override
    public List<Module> getKompileModules() {
        return Collections.singletonList(new AbstractModule() {
            @Override
            protected void configure() {
                configureKompileModules(binder());
            }
        });
    }

    @Override
    public List<Module> getDefinitionSpecificKRunModules() {
        return Collections.singletonList(new AbstractModule() {
            @Override
            protected void configure() {
                configureDefinitionSpecificKRunModules(binder());
            }
        });
    }

    private void configureKompileModules(Binder binder) {
        MapBinder<String, Consumer<CompiledDefinition>> mapBinder;
        mapBinder = MapBinder.newMapBinder(binder,
                                           TypeLiteral.get(String.class),
                                           new TypeLiteral<Consumer<CompiledDefinition>>() {});
        mapBinder.addBinding("func").to(FuncBackend.class);
    }

    private void configureDefinitionSpecificKRunModules(Binder binder) {
        MapBinder<String, Function<org.kframework.definition.Module, Rewriter>> rewriterBinder;
        rewriterBinder = MapBinder.newMapBinder(binder,
                                                TypeLiteral.get(String.class),
                                                new TypeLiteral<Function<org.kframework.definition.Module, Rewriter>>() {});
        rewriterBinder.addBinding("func").to(FuncRewriter.class);
    }
}
