/*
 * #%L
 * Alfresco Transform Core
 * %%
 * Copyright (C) 2005 - 2022 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * -
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 * -
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * -
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * -
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.transformer;

import org.alfresco.transform.client.model.config.TransformConfig;
import org.alfresco.transform.exceptions.TransformException;
import org.alfresco.transformer.executors.Transformer;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.alfresco.transform.client.model.Mimetype.MIMETYPE_HTML;
import static org.alfresco.transform.client.model.Mimetype.MIMETYPE_TEXT_PLAIN;
import static org.alfresco.transform.client.model.config.CoreVersionDecorator.setOrClearCoreVersion;
import static org.alfresco.transform.client.util.RequestParamMap.CONFIG_VERSION_DEFAULT;
import static org.alfresco.transformer.util.RequestParamMap.CONFIG_VERSION;
import static org.alfresco.transformer.util.RequestParamMap.SOURCE_ENCODING;
import static org.alfresco.transformer.util.RequestParamMap.TRANSFORM_NAME_PARAMETER;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;

@Controller
public class AIOController extends AbstractTransformerController
{
    private static final Logger logger = LoggerFactory.getLogger(AIOController.class);

    @Autowired
    private  AIOTransformRegistry transformRegistry;

    @Override
    public String getTransformerName()
    {
        return "All in One Transformer";
    }

    @Override
    public String version()
    {
        return getTransformerName() + " available";
    }

    @Override
    public ProbeTestTransform getProbeTestTransform() 
    {
        return new ProbeTestTransform(this, "quick.html", "quick.txt",
            119, 30, 150, 1024,
            60 * 2 + 1, 60 * 2)
        {
            @Override
            protected void executeTransformCommand(File sourceFile, File targetFile)
            {
                Map<String, String> parameters = new HashMap<>();
                parameters.put(SOURCE_ENCODING, "UTF-8");
                transformImpl("html", MIMETYPE_HTML, MIMETYPE_TEXT_PLAIN, parameters, sourceFile, targetFile);
            }
        };
    }

    @Override
    public ResponseEntity<TransformConfig> info(
            @RequestParam(value = CONFIG_VERSION, defaultValue = CONFIG_VERSION_DEFAULT) int configVersion)
    {
        logger.info("GET Transform Config version: " + configVersion);
        TransformConfig transformConfig = transformRegistry.getTransformConfig();
        transformConfig = setOrClearCoreVersion(transformConfig, configVersion);
        return new ResponseEntity<>(transformConfig, OK);
    }

    @Override
    public void transformImpl(String transformName, String sourceMimetype, String targetMimetype,
                                 Map<String, String> transformOptions, File sourceFile, File targetFile)
    {
        logger.debug("Processing transform with: transformName; '{}', sourceFile '{}', targetFile '{}', transformOptions" +
                " {}", transformName, sourceFile, targetFile, transformOptions);

        Transformer transformer = transformRegistry.getByTransformName(transformName);
        if (transformer == null)
        {
            throw new TransformException(INTERNAL_SERVER_ERROR.value(), "No transformer mapping for - transform:"
                    + transformName + " sourceMimetype:" + sourceMimetype + " targetMimetype:" + targetMimetype);
        }

        if (logger.isDebugEnabled())
        {
            logger.debug("Performing transform with name '{}' using transformer with id '{}'.", transformName, transformer.getTransformerId());
        }

        transformer.transformExtractOrEmbed(transformName, sourceMimetype, targetMimetype, transformOptions, sourceFile, targetFile);
    }
}
