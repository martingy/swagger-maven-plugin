package com.github.kongchen.swagger.docgen.mavenplugin;

import com.github.kongchen.swagger.docgen.AbstractDocumentSource;
import com.github.kongchen.swagger.docgen.GenerateException;
import com.github.kongchen.swagger.docgen.reader.ClassSwaggerReader;
import com.github.kongchen.swagger.docgen.reader.JaxrsReader;
import io.swagger.annotations.Api;
import io.swagger.config.FilterFactory;
import io.swagger.core.filter.SpecFilter;
import io.swagger.core.filter.SwaggerSpecFilter;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.DefaultReaderConfig;
import io.swagger.models.auth.SecuritySchemeDefinition;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import javax.ws.rs.Path;
import java.util.*;

/**
 * @author chekong
 *         05/13/2013
 */
public class MavenDocumentSource extends AbstractDocumentSource {
    private final SpecFilter specFilter = new SpecFilter();

    public MavenDocumentSource(ApiSource apiSource, Log log) throws MojoFailureException {
        super(log, apiSource);
    }

    @Override
    public void loadDocuments() throws GenerateException {
        if (apiSource.getSwaggerInternalFilter() != null) {
            try {
                LOG.info("Setting filter configuration: " + apiSource.getSwaggerInternalFilter());
                FilterFactory.setFilter((SwaggerSpecFilter) Class.forName(apiSource.getSwaggerInternalFilter()).newInstance());
            } catch (Exception e) {
                throw new GenerateException("Cannot load: " + apiSource.getSwaggerInternalFilter(), e);
            }
        }

        Set<Class<?>> validClasses = apiSource.getValidClasses(Api.class);
        // If scanAllResources is true, Path annotations should be scanned too
        if (apiSource.isScanAllResources() && "io.swagger.jaxrs.Reader".equals(apiSource.getSwaggerApiReader())) {
            validClasses.addAll(apiSource.getValidClasses(Path.class));
            DefaultReaderConfig rc = new DefaultReaderConfig();
            rc.setScanAllResources(true);
            Reader reader = new Reader(swagger, rc);
            swagger = reader.read(validClasses);
        } else {
            swagger = resolveApiReader().read(validClasses);
        }

        if (apiSource.getSecurityDefinitions() != null) {
            for (SecurityDefinition sd : apiSource.getSecurityDefinitions()) {
                for (Map.Entry<String, SecuritySchemeDefinition> entry : sd.getDefinitions().entrySet()) {
                    swagger.addSecurityDefinition(entry.getKey(), entry.getValue());
                }
            }
        }

        // sort security defs to make output consistent
        Map<String, SecuritySchemeDefinition> defs = swagger.getSecurityDefinitions();
        if (defs != null) {
            Map<String, SecuritySchemeDefinition> sortedDefs = new TreeMap<String, SecuritySchemeDefinition>();
            sortedDefs.putAll(defs);
            swagger.setSecurityDefinitions(sortedDefs);
        }

        if (FilterFactory.getFilter() != null) {
            swagger = new SpecFilter().filter(swagger, FilterFactory.getFilter(),
                    new HashMap<String, List<String>>(), new HashMap<String, String>(),
                    new HashMap<String, List<String>>());
        }
    }

    private ClassSwaggerReader resolveApiReader() throws GenerateException {
        String customReaderClassName = apiSource.getSwaggerApiReader();
        if (customReaderClassName == null) {
            JaxrsReader reader = new JaxrsReader(swagger, LOG);
            reader.setTypesToSkip(this.typesToSkip);
            return reader;
        } else {
            return getCustomApiReader(customReaderClassName);
        }
    }
}
