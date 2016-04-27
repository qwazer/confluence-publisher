/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.confluence.publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.sahli.confluence.publisher.http.ConfluenceRestClient;
import org.sahli.confluence.publisher.metadata.ConfluencePageMetadata;
import org.sahli.confluence.publisher.metadata.ConfluencePublisherMetadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.sahli.confluence.publisher.utils.AssertUtils.assertMandatoryParameter;
import static org.sahli.confluence.publisher.utils.InputStreamUtils.fileContent;

/**
 * @author Alain Sahli
 * @since 1.0
 */
public class ConfluencePublisher {

    private final ConfluencePublisherMetadata metadata;
    private final ConfluenceRestClient confluenceRestClient;
    private final String contentRoot;

    public ConfluencePublisher(String metadataFilePath, ConfluenceRestClient confluenceRestClient) {
        this.metadata = readConfig(metadataFilePath);
        this.contentRoot = new File(metadataFilePath).getParentFile().getAbsoluteFile().toString();
        this.confluenceRestClient = confluenceRestClient;
    }

    public ConfluencePublisherMetadata getMetadata() {
        return this.metadata;
    }

    public void publish() {
        assertMandatoryParameter(isNotBlank(this.getMetadata().getSpaceKey()), "spaceKey");

        if (isNotBlank(this.metadata.getAncestorId())) {
            startPublishingUnderAncestorId(this.metadata.getPages(), this.metadata.getSpaceKey(), this.metadata.getAncestorId());
        } else {
            startPublishingUnderSpace(this.metadata.getPages(), this.metadata.getSpaceKey());
        }
    }

    private void startPublishingUnderSpace(List<ConfluencePageMetadata> pages, String spaceKey) {
        pages.forEach(page -> {
            String content = fileContent(Paths.get(this.contentRoot, page.getContentFilePath()).toString());
            String contentId = this.confluenceRestClient.addPageUnderSpace(spaceKey, page.getTitle(), content);

            addAttachments(contentId, page.getAttachments());
            startPublishingUnderAncestorId(page.getChildren(), spaceKey, contentId);
        });
    }

    private void startPublishingUnderAncestorId(List<ConfluencePageMetadata> pages, String spaceKey, String ancestorId) {
        pages.forEach(page -> {
            String content = fileContent(Paths.get(this.contentRoot, page.getContentFilePath()).toString());
            String contentId = this.confluenceRestClient.addPageUnderAncestor(spaceKey, ancestorId, page.getTitle(), content);

            addAttachments(contentId, page.getAttachments());
            startPublishingUnderAncestorId(page.getChildren(), spaceKey, contentId);
        });
    }

    private void addAttachments(String contentId, List<String> attachments) {
        attachments.forEach(attachment -> {
            FileInputStream inputStream = fileInputStream(Paths.get(this.contentRoot, attachment).toString());
            this.confluenceRestClient.addAttachment(contentId, attachment, inputStream);
        });
    }

    private static ConfluencePublisherMetadata readConfig(String configPath) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        try {
            return objectMapper.readValue(new File(configPath), ConfluencePublisherMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read metadata", e);
        }
    }

    private static FileInputStream fileInputStream(String filePath) {
        try {
            return new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could not find attachment ", e);
        }
    }

}
