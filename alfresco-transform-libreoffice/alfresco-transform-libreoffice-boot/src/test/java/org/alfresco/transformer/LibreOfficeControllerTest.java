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

import static org.alfresco.transform.client.model.Mimetype.MIMETYPE_PDF;
import static org.alfresco.transform.client.util.RequestParamMap.ENDPOINT_TRANSFORM;
import static org.alfresco.transformer.util.RequestParamMap.SOURCE_MIMETYPE;
import static org.alfresco.transformer.util.RequestParamMap.TARGET_EXTENSION;
import static org.alfresco.transformer.util.RequestParamMap.TARGET_MIMETYPE;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.util.StringUtils.getFilenameExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;

import javax.annotation.PostConstruct;

import org.alfresco.transform.client.model.TransformReply;
import org.alfresco.transform.client.model.TransformRequest;
import org.alfresco.transformer.executors.LibreOfficeJavaExecutor;
import org.alfresco.transformer.executors.RuntimeExec.ExecutionResult;
import org.alfresco.transformer.model.FileRefEntity;
import org.alfresco.transformer.model.FileRefResponse;
import org.artofsolving.jodconverter.office.OfficeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Test the LibreOfficeController without a server.
 * Super class includes tests for the AbstractTransformerController.
 */
// Specifying class for @WebMvcTest() will break AIO tests, without specifying it will use all controllers in the application context,
// currently only LibreOfficeController.class
@WebMvcTest()
public class LibreOfficeControllerTest extends AbstractTransformerControllerTest
{

    protected static final String ENGINE_CONFIG_NAME = "libreoffice_engine_config.json";
    protected String targetMimetype = MIMETYPE_PDF;

    @Mock
    protected ExecutionResult mockExecutionResult;

    @Value("${transform.core.libreoffice.path}")
    private String execPath;

    @Value("${transform.core.libreoffice.maxTasksPerProcess}")
    private String maxTasksPerProcess;

    @Value("${transform.core.libreoffice.timeout}")
    private String timeout;

    @Value("${transform.core.libreoffice.portNumbers}")
    private String portNumbers;

    @Value("${transform.core.libreoffice.templateProfileDir}")
    private String templateProfileDir;

    @Value("${transform.core.libreoffice.isEnabled}")
    private String isEnabled;

    protected LibreOfficeJavaExecutor javaExecutor;

    @PostConstruct
    private void init()
    {
        javaExecutor = Mockito.spy(new LibreOfficeJavaExecutor(execPath, maxTasksPerProcess, timeout, portNumbers, templateProfileDir, isEnabled));
    }

    @Autowired
    protected AbstractTransformerController controller;

    @BeforeEach
    public void before() throws IOException
    {
        sourceExtension = "doc";
        targetExtension = "pdf";
        sourceMimetype = "application/msword";

        setJavaExecutor(controller,javaExecutor);

        // The following is based on super.mockTransformCommand(...)
        // This is because LibreOffice used JodConverter rather than a RuntimeExec

        expectedSourceFileBytes = Files.readAllBytes(
            getTestFile("quick." + sourceExtension, true).toPath());
        expectedTargetFileBytes = Files.readAllBytes(
            getTestFile("quick." + targetExtension, true).toPath());
        sourceFile = new MockMultipartFile("file", "quick." + sourceExtension, sourceMimetype,
            expectedSourceFileBytes);

        doAnswer(invocation ->
        {
            File sourceFile = invocation.getArgument(0);
            File targetFile = invocation.getArgument(1);
            String actualTargetExtension = getFilenameExtension(targetFile.getAbsolutePath());

            assertNotNull(sourceFile);
            assertNotNull(targetFile);

            // Copy a test file into the target file location if it exists
            String actualTarget = targetFile.getAbsolutePath();
            int i = actualTarget.lastIndexOf('_');
            if (i >= 0)
            {
                String testFilename = actualTarget.substring(i + 1);
                File testFile = getTestFile(testFilename, false);
                generateTargetFileFromResourceFile(actualTargetExtension, testFile, targetFile);
            }

            // Check the supplied source file has not been changed.
            byte[] actualSourceFileBytes = Files.readAllBytes(sourceFile.toPath());
            assertTrue(Arrays.equals(expectedSourceFileBytes, actualSourceFileBytes), "Source file is not the same");

            return null;
        }).when(javaExecutor).convert(any(), any());
    }

    
    protected void setJavaExecutor(AbstractTransformerController controller, LibreOfficeJavaExecutor javaExecutor)
    {
        ReflectionTestUtils.setField(controller, "javaExecutor", javaExecutor);
    }

    @Override
    public String getEngineConfigName()
    {
        return ENGINE_CONFIG_NAME;
    }

    @Override
    protected void mockTransformCommand(String sourceExtension, String targetExtension,
        String sourceMimetype, boolean readTargetFileBytes)
    {
        throw new IllegalStateException();
    }

    @Override
    protected AbstractTransformerController getController()
    {
        return controller;
    }

    @Test
    public void badExitCodeTest() throws Exception
    {
        doThrow(OfficeException.class).when(javaExecutor).convert(any(), any());

        mockMvc
            .perform(MockMvcRequestBuilders
                .multipart(ENDPOINT_TRANSFORM)
                .file(sourceFile)
                .param(TARGET_EXTENSION, "xxx")
                .param(SOURCE_MIMETYPE,sourceMimetype)
                .param(TARGET_MIMETYPE,targetMimetype))
            .andExpect(status().is(400))
            .andExpect(status().reason(
                containsString("LibreOffice server conversion failed:")));
    }

    @Override
    protected void updateTransformRequestWithSpecificOptions(TransformRequest transformRequest)
    {
        transformRequest.setSourceExtension("doc");
        transformRequest.setTargetExtension("pdf");
        transformRequest.setSourceMediaType("application/msword");
        transformRequest.setTargetMediaType(targetMimetype);
    }

    @Test
    public void testPojoTransform() throws Exception
    {
        // Files
        String sourceFileRef = UUID.randomUUID().toString();
        File sourceFile = getTestFile("quick." + sourceExtension, true);
        String targetFileRef = UUID.randomUUID().toString();

        TransformRequest transformRequest = createTransformRequest(sourceFileRef, sourceFile);

        // HTTP Request
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_DISPOSITION, "attachment; filename=quick." + sourceExtension);
        ResponseEntity<Resource> response = new ResponseEntity<>(new FileSystemResource(
            sourceFile), headers, OK);

        when(alfrescoSharedFileStoreClient.retrieveFile(sourceFileRef)).thenReturn(response);
        when(alfrescoSharedFileStoreClient.saveFile(any()))
            .thenReturn(new FileRefResponse(new FileRefEntity(targetFileRef)));
        when(mockExecutionResult.getExitValue()).thenReturn(0);

        // Update the Transformation Request with any specific params before sending it
        updateTransformRequestWithSpecificOptions(transformRequest);

        // Serialize and call the transformer
        String tr = objectMapper.writeValueAsString(transformRequest);
        String transformationReplyAsString = mockMvc
            .perform(MockMvcRequestBuilders
                .post(ENDPOINT_TRANSFORM)
                .header(ACCEPT, APPLICATION_JSON_VALUE)
                .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .content(tr))
            .andExpect(status().is(CREATED.value()))
            .andReturn().getResponse().getContentAsString();

        TransformReply transformReply = objectMapper.readValue(transformationReplyAsString,
            TransformReply.class);

        // Assert the reply
        assertEquals(transformRequest.getRequestId(), transformReply.getRequestId());
        assertEquals(transformRequest.getClientData(), transformReply.getClientData());
        assertEquals(transformRequest.getSchema(), transformReply.getSchema());
    }

    @Test
    public void testOverridingExecutorPaths()
    {
        //System test property value can me modified in the pom.xml
        assertEquals(execPath, System.getProperty("LIBREOFFICE_HOME"));
    }

    @Test
    public void testOverridingExecutorMaxTasksPerProcess()
    {
        //System test property value can me modified in the pom.xml
        assertEquals(maxTasksPerProcess, System.getProperty("LIBREOFFICE_MAX_TASKS_PER_PROCESS"));
    }

    @Test
    public void testOverridingExecutorTimeout()
    {
        //System test property value can me modified in the pom.xml
        assertEquals(timeout, System.getProperty("LIBREOFFICE_TIMEOUT"));
    }

    @Test
    public void testOverridingExecutorPortNumbers()
    {
        //System test property value can me modified in the pom.xml
        assertEquals(portNumbers, System.getProperty("LIBREOFFICE_PORT_NUMBERS"));
    }

    @Test
    public void testOverridingExecutorTemplateProfileDir()
    {
        //System test property value can me modified in the pom.xml
        assertEquals(templateProfileDir, System.getProperty("LIBREOFFICE_TEMPLATE_PROFILE_DIR"));
    }

    @Test
    public void testOverridingExecutorIsEnabled()
    {
        //System test property value can me modified in the pom.xml
        assertEquals(isEnabled, System.getProperty("LIBREOFFICE_IS_ENABLED"));
    }

    @Test
    public void testInvalidExecutorMaxTasksPerProcess()
    {
        String errorMessage = "";
        try
        {
            new LibreOfficeJavaExecutor(execPath, "INVALID", timeout, portNumbers, templateProfileDir, isEnabled);
        }
        catch (IllegalArgumentException e)
        {
            errorMessage = e.getMessage();
        }

        assertEquals("LibreOfficeJavaExecutor MAX_TASKS_PER_PROCESS must have a numeric value", errorMessage);
    }

    @Test
    public void testInvalidExecutorTimeout()
    {
        String errorMessage = "";
        try
        {
            new LibreOfficeJavaExecutor(execPath, maxTasksPerProcess, "INVALID", portNumbers, templateProfileDir, isEnabled);
        }
        catch (IllegalArgumentException e)
        {
            errorMessage = e.getMessage();
        }

        assertEquals("LibreOfficeJavaExecutor TIMEOUT must have a numeric value", errorMessage);
    }

    @Test
    public void testInvalidExecutorPortNumbers()
    {
        String errorMessage = "";
        try
        {
            new LibreOfficeJavaExecutor(execPath, maxTasksPerProcess, timeout, null, templateProfileDir, isEnabled);
        }
        catch (IllegalArgumentException e)
        {
            errorMessage = e.getMessage();
        }

        assertEquals("LibreOfficeJavaExecutor PORT variable cannot be null or empty", errorMessage);
    }

    @Test
    public void testInvalidExecutorIsEnabled()
    {
        String errorMessage = "";
        try
        {
            new LibreOfficeJavaExecutor(execPath, maxTasksPerProcess, timeout, portNumbers, templateProfileDir, "INVALID");
        }
        catch (IllegalArgumentException e)
        {
            errorMessage = e.getMessage();
        }

        assertEquals("LibreOfficeJavaExecutor IS_ENABLED variable must be set to true/false", errorMessage);
    }
}
