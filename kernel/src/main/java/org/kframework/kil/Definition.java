// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil;

import org.kframework.compile.sharing.DataStructureSortCollector;
import org.kframework.compile.sharing.TokenSortCollector;
import org.kframework.kil.loader.*;
import org.kframework.kil.visitors.Visitor;
import org.kframework.parser.DefinitionLoader;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.Poset;

import com.google.inject.Inject;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Represents a language definition.
 * Includes contents from all {@code required}-d files.
 * @see DefinitionLoader
 */
public class Definition extends ASTNode implements Interfaces.MutableList<DefinitionItem, Enum<?>> {

    private List<DefinitionItem> items;
    private File mainFile;
    private String mainModule;
    /** An index of all modules in {@link #items} by name */
    private Map<String, Module> modulesMap;
    private String mainSyntaxModule;
    private Poset<String> modules = Poset.create();
    public Map<String, ASTNode> locations = new HashMap<>();

    public Definition() {
        super();
    }

    @Inject
    public Definition(Void v) {}

    public Definition(Definition d) {
        super(d);
        this.mainFile = d.mainFile;
        this.mainModule = d.mainModule;
        this.mainSyntaxModule = d.mainSyntaxModule;
        this.items = d.items;
        this.locations = d.locations;
    }

    @Override
    public String toString() {
        String content = "";
        for (DefinitionItem di : items)
            content += di + " \n";

        return "DEF: " + mainFile + " -> " + mainModule + "\n" + content;
    }

    public void addModuleImport(String mainModule, String importedModule) {
        if (mainModule.equals(importedModule))
            return;
        modules.addRelation(mainModule, importedModule);
    }

    public boolean isModuleIncludedEq(String localModule, String importedModule) {
        if (localModule.equals(importedModule))
            return true;
        return modules.isInRelation(localModule, importedModule);
    }

    public void finalizeModules() {
        modules.transitiveClosure();
    }

    public void setItems(List<DefinitionItem> items) {
        this.items = items;
    }

    public List<DefinitionItem> getItems() {
        return items;
    }

    public void setMainFile(File mainFile) {
        this.mainFile = mainFile;
    }

    public File getMainFile() {
        return mainFile;
    }

    public void setMainModule(String mainModule) {
        this.mainModule = mainModule;
    }

    public String getMainModule() {
        return mainModule;
    }

    public void setMainSyntaxModule(String mainSyntaxModule) {
        this.mainSyntaxModule = mainSyntaxModule;
    }

    public String getMainSyntaxModule() {
        return mainSyntaxModule;
    }

    public void preprocess(org.kframework.kil.loader.Context context) {
        // Collect information
        // this.accept(new AddSymbolicVariablesDeclaration(context, this.getMainSyntaxModule()));
        new UpdateReferencesVisitor(context).visitNode(this);
        new CollectProductionsVisitor(context).visitNode(this);
        new UpdateAssocVisitor(context).visitNode(this);
        context.computeConses();
        new CollectBracketsVisitor(context).visitNode(this);
        new CollectSubsortsVisitor(context).visitNode(this);
        new CollectPrioritiesVisitor(context).visitNode(this);
        new CollectStartSymbolPgmVisitor(context).visitNode(this);
        new CollectConfigCellsVisitor(context).visitNode(this);
        new CollectLocationsVisitor().visitNode(this);
        new CollectVariableTokens(context).visitNode(this);

        /* collect lexical token sorts */
        context.setTokenSorts(TokenSortCollector.collectTokenSorts(this, context));

        /* collect the data structure sorts */
        DataStructureSortCollector dataStructureSortCollector
                = new DataStructureSortCollector(context);
        dataStructureSortCollector.visitNode(this);
        context.setDataStructureSorts(dataStructureSortCollector.getSorts());

        context.makeFreshFunctionNamesMap(this.getSyntaxByTag(Attribute.FRESH_GENERATOR, context));
    }

    public Map<String, Module> getModulesMap() {
        return modulesMap;
    }

    public void setModulesMap(Map<String, Module> modulesMap) {
        this.modulesMap = modulesMap;
    }

    public Module getSingletonModule() {
        List<Module> modules = new LinkedList<Module>();
        for (DefinitionItem i : this.getItems()) {
            if (i instanceof Module)
                modules.add((Module) i);
        }
        if (modules.size() != 1) {
            String msg = "Should have been only one module when calling this method.";
            throw KExceptionManager.internalError(msg, this);
        }
        return modules.get(0);
    }

    @Override
    public Definition shallowCopy() {
        return new Definition(this);
    }

    @Override
    protected <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.complete(this, visitor.visit(this, p));
    }

    @Override
    public List<DefinitionItem> getChildren(Enum<?> _) {
        return items;
    }

    @Override
    public void setChildren(List<DefinitionItem> children, Enum<?> _) {
        this.items = children;
    }
}
