// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.func;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import org.kframework.backend.Backend;
import org.kframework.backend.func.FuncBackend;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.krun.ioserver.filesystem.portable.PortableFileSystem;

/**
 * Created by taktoa on 5/25/15.
 */
public class FuncBackendKompileModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(FileSystem.class).to(PortableFileSystem.class);

        MapBinder<String, Backend> mapBinder = MapBinder.newMapBinder(
                binder(), String.class, Backend.class);
        mapBinder.addBinding("func").to(FuncBackend.class);
    }
}
