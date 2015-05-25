// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import org.kframework.main.KModule;

import java.util.List;

/**
 * Created by taktoa on 5/25/15.
 */
public class FuncBackendModule implements KModule {
    @Override
    public List<Module> getKDocModules() {
        return ImmutableList.of();
    }

    @Override
    public List<Module> getKompileModules() {
        return ImmutableList.of(new FuncBackendKompileModule());
    }

    @Override
    public List<Module> getKastModules() {
        return ImmutableList.of();
    }

    @Override
    public List<Module> getKRunModules(List<Module> definitionSpecificModules) {
        return ImmutableList.of();
    }

    @Override
    public List<Module> getDefinitionSpecificKRunModules() {
        return ImmutableList.of();
    }

    @Override
    public List<Module> getKTestModules() {
        return ImmutableList.of();
    }
}
