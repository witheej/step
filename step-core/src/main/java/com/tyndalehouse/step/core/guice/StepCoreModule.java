package com.tyndalehouse.step.core.guice;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.crosswire.jsword.book.install.Installer;

import com.avaje.ebean.EbeanServer;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.tyndalehouse.step.core.data.create.Loader;
import com.tyndalehouse.step.core.guice.providers.DatabaseConfigProvider;
import com.tyndalehouse.step.core.guice.providers.DefaultInstallersProvider;
import com.tyndalehouse.step.core.guice.providers.DefaultLexiconRefsProvider;
import com.tyndalehouse.step.core.guice.providers.DefaultVersionsProvider;
import com.tyndalehouse.step.core.service.BibleInformationService;
import com.tyndalehouse.step.core.service.JSwordService;
import com.tyndalehouse.step.core.service.ModuleService;
import com.tyndalehouse.step.core.service.TimelineService;
import com.tyndalehouse.step.core.service.impl.BibleInformationServiceImpl;
import com.tyndalehouse.step.core.service.impl.JSwordServiceImpl;
import com.tyndalehouse.step.core.service.impl.ModuleServiceImpl;
import com.tyndalehouse.step.core.service.impl.TimelineServiceImpl;

/**
 * The module configuration that configures the application via guice
 * 
 * @author Chris
 * 
 */
public class StepCoreModule extends AbstractModule implements Module {
    private static final String CORE_GUICE_PROPERTIES = "/step.core.properties";

    @Override
    protected void configure() {
        bind(Properties.class).annotatedWith(Names.named("StepCoreProperties")).toInstance(readProperties());

        bind(JSwordService.class).to(JSwordServiceImpl.class).asEagerSingleton();
        bind(BibleInformationService.class).to(BibleInformationServiceImpl.class).asEagerSingleton();
        bind(ModuleService.class).to(ModuleServiceImpl.class).asEagerSingleton();
        bind(TimelineService.class).to(TimelineServiceImpl.class);
        bind(Loader.class);

        bind(new TypeLiteral<List<String>>() {
        }).annotatedWith(Names.named("defaultVersions")).toProvider(DefaultVersionsProvider.class);
        bind(new TypeLiteral<Map<String, String>>() {
        }).annotatedWith(Names.named("defaultLexiconRefs")).toProvider(DefaultLexiconRefsProvider.class);
        bind(new TypeLiteral<List<Installer>>() {
        }).toProvider(DefaultInstallersProvider.class);

        bind(EbeanServer.class).toProvider(DatabaseConfigProvider.class).asEagerSingleton();

        bindDaos();
        // bind(ConnectionSource.class).toProvider(DataSourceProvider.class);
    }

    /**
     * helper method that binds the DAOs
     */
    private void bindDaos() {
        // bind(TimebandDao.class).to(TimebandDaoImpl.class);
        // bind(HotSpotDao.class).to(HotSpotDaoImpl.class);
        // bind(TimelineEventDao.class).to(TimelineEventDaoImpl.class);
        // bind(ScriptureReferenceDao.class).to(ScriptureReferenceDaoImpl.class);
    }

    /**
     * reads the core properties from the file
     * 
     * @return a list of properties read from file
     */
    private Properties readProperties() {
        final InputStream stream = getClass().getResourceAsStream(CORE_GUICE_PROPERTIES);
        final Properties appProperties = new Properties();
        try {
            appProperties.load(stream);
            Names.bindProperties(super.binder(), appProperties);
        } catch (final IOException e) {
            // This is the preferred way to tell Guice something went wrong
            super.addError(e);
        }
        return appProperties;
    }

}
