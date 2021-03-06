/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.azure.spring.cloud.config;

import static com.microsoft.azure.spring.cloud.config.Constants.FEATURE_FLAG_CONTENT_TYPE;
import static com.microsoft.azure.spring.cloud.config.TestConstants.FEATURE_LABEL;
import static com.microsoft.azure.spring.cloud.config.TestConstants.FEATURE_VALUE;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_CONN_STRING;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_CONTEXT;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_KEY_1;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_KEY_2;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_KEY_3;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_LABEL_1;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_LABEL_2;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_LABEL_3;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_SLASH_KEY;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_SLASH_VALUE;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_STORE_NAME;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_VALUE_1;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_VALUE_2;
import static com.microsoft.azure.spring.cloud.config.TestConstants.TEST_VALUE_3;
import static com.microsoft.azure.spring.cloud.config.TestUtils.createItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.azure.core.http.rest.PagedFlux;
import com.azure.core.http.rest.PagedResponse;
import com.azure.data.appconfiguration.ConfigurationAsyncClient;
import com.azure.data.appconfiguration.models.ConfigurationSetting;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.spring.cloud.config.feature.management.entity.Feature;
import com.microsoft.azure.spring.cloud.config.feature.management.entity.FeatureFilterEvaluationContext;
import com.microsoft.azure.spring.cloud.config.feature.management.entity.FeatureSet;
import com.microsoft.azure.spring.cloud.config.stores.ClientStore;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public class AzureConfigPropertySourceTest {
    private static final String EMPTY_CONTENT_TYPE = "";

    private static final AzureCloudConfigProperties TEST_PROPS = new AzureCloudConfigProperties();

    public static final List<ConfigurationSetting> TEST_ITEMS = new ArrayList<>();

    public static final List<ConfigurationSetting> FEATURE_ITEMS = new ArrayList<>();

    private static final ConfigurationSetting item1 = createItem(TEST_CONTEXT, TEST_KEY_1, TEST_VALUE_1, TEST_LABEL_1,
            EMPTY_CONTENT_TYPE);

    private static final ConfigurationSetting item2 = createItem(TEST_CONTEXT, TEST_KEY_2, TEST_VALUE_2, TEST_LABEL_2,
            EMPTY_CONTENT_TYPE);

    private static final ConfigurationSetting item3 = createItem(TEST_CONTEXT, TEST_KEY_3, TEST_VALUE_3, TEST_LABEL_3,
            EMPTY_CONTENT_TYPE);

    private static final ConfigurationSetting item3Null = createItem(TEST_CONTEXT, TEST_KEY_3, TEST_VALUE_3,
            TEST_LABEL_3,
            null);

    private static final ConfigurationSetting featureItem = createItem(".appconfig.featureflag/", "Alpha",
            FEATURE_VALUE, FEATURE_LABEL, FEATURE_FLAG_CONTENT_TYPE);

    private static final ConfigurationSetting featureItemNull = createItem(".appconfig.featureflag/", "Alpha",
            FEATURE_VALUE,
            FEATURE_LABEL, null);

    public List<ConfigurationSetting> testItems = new ArrayList<>();

    private static final String FEATURE_MANAGEMENT_KEY = "feature-management.featureManagement";

    private AzureConfigPropertySource propertySource;

    private static ObjectMapper mapper = new ObjectMapper();

    private AzureCloudConfigProperties azureProperties;

    private AppConfigProviderProperties appProperties;

    @Mock
    private ClientStore clientStoreMock;

    @Mock
    private ConfigurationAsyncClient configClientMock;

    @Mock
    private PagedFlux<ConfigurationSetting> settingsMock;

    @Mock
    private Flux<PagedResponse<ConfigurationSetting>> pageMock;

    @Mock
    private Mono<List<PagedResponse<ConfigurationSetting>>> collectionMock;

    @Mock
    private List<PagedResponse<ConfigurationSetting>> itemsMock;

    @Mock
    private Iterator<PagedResponse<ConfigurationSetting>> itemsIteratorMock;

    @Mock
    private PagedResponse<ConfigurationSetting> pagedResponseMock;

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @BeforeClass
    public static void init() {
        TestUtils.addStore(TEST_PROPS, TEST_STORE_NAME, TEST_CONN_STRING);

        featureItem.setContentType(FEATURE_FLAG_CONTENT_TYPE);
        FEATURE_ITEMS.add(featureItem);
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        azureProperties = new AzureCloudConfigProperties();
        azureProperties.setFailFast(true);
        appProperties = new AppConfigProviderProperties();
        appProperties.setKeyVaultWaitTime(0);
        propertySource = new AzureConfigPropertySource(TEST_CONTEXT, TEST_STORE_NAME, "\0",
                azureProperties, appProperties, clientStoreMock);

        testItems = new ArrayList<ConfigurationSetting>();
        testItems.add(item1);
        testItems.add(item2);
        testItems.add(item3);

        when(configClientMock.listSettings(Mockito.any())).thenReturn(settingsMock);
        when(settingsMock.byPage()).thenReturn(pageMock);
        when(pageMock.collectList()).thenReturn(collectionMock);
        when(collectionMock.block()).thenReturn(itemsMock);
        when(itemsMock.iterator()).thenReturn(itemsIteratorMock);
        when(itemsIteratorMock.next()).thenReturn(pagedResponseMock);
    }

    @Test
    public void testPropCanBeInitAndQueried() throws ServerException {
        when(clientStoreMock.listSettings(Mockito.any(), Mockito.anyString())).thenReturn(testItems)
                .thenReturn(FEATURE_ITEMS);
        FeatureSet featureSet = new FeatureSet();
        try {
            propertySource.initProperties(featureSet);
        } catch (IOException e) {
            fail("Failed Reading in Feature Flags");
        }
        propertySource.initFeatures(featureSet);

        String[] keyNames = propertySource.getPropertyNames();
        String[] expectedKeyNames = testItems.stream()
                .map(t -> t.getKey().substring(TEST_CONTEXT.length())).toArray(String[]::new);
        String[] allExpectedKeyNames = ArrayUtils.addAll(expectedKeyNames, FEATURE_MANAGEMENT_KEY);

        assertThat(keyNames).containsExactlyInAnyOrder(allExpectedKeyNames);

        assertThat(propertySource.getProperty(TEST_KEY_1)).isEqualTo(TEST_VALUE_1);
        assertThat(propertySource.getProperty(TEST_KEY_2)).isEqualTo(TEST_VALUE_2);
        assertThat(propertySource.getProperty(TEST_KEY_3)).isEqualTo(TEST_VALUE_3);
    }

    @Test
    public void testPropertyNameSlashConvertedToDots() throws ServerException {
        ConfigurationSetting slashedProp = createItem(TEST_CONTEXT, TEST_SLASH_KEY, TEST_SLASH_VALUE, null,
                EMPTY_CONTENT_TYPE);
        List<ConfigurationSetting> settings = new ArrayList<ConfigurationSetting>();
        settings.add(slashedProp);
        when(clientStoreMock.listSettings(Mockito.any(), Mockito.anyString())).thenReturn(settings)
                .thenReturn(new ArrayList<ConfigurationSetting>());
        FeatureSet featureSet = new FeatureSet();
        try {
            propertySource.initProperties(featureSet);
        } catch (IOException e) {
            fail("Failed Reading in Feature Flags");
        }

        String expectedKeyName = TEST_SLASH_KEY.replace('/', '.');
        String[] actualKeyNames = propertySource.getPropertyNames();

        assertThat(actualKeyNames.length).isEqualTo(1);
        assertThat(actualKeyNames[0]).isEqualTo(expectedKeyName);
        assertThat(propertySource.getProperty(TEST_SLASH_KEY)).isNull();
        assertThat(propertySource.getProperty(expectedKeyName)).isEqualTo(TEST_SLASH_VALUE);
    }

    @Test
    public void testFeatureFlagCanBeInitedAndQueried() throws ServerException {
        when(clientStoreMock.listSettings(Mockito.any(), Mockito.anyString()))
                .thenReturn(new ArrayList<ConfigurationSetting>()).thenReturn(FEATURE_ITEMS);

        FeatureSet featureSet = new FeatureSet();
        try {
            propertySource.initProperties(featureSet);
        } catch (IOException e) {
            fail("Failed Reading in Feature Flags");
        }
        propertySource.initFeatures(featureSet);

        FeatureSet featureSetExpected = new FeatureSet();
        Feature feature = new Feature();
        feature.setKey("Alpha");
        ArrayList<FeatureFilterEvaluationContext> filters = new ArrayList<FeatureFilterEvaluationContext>();
        FeatureFilterEvaluationContext ffec = new FeatureFilterEvaluationContext();
        ffec.setName("TestFilter");
        filters.add(ffec);
        feature.setEnabledFor(filters);
        featureSetExpected.addFeature("Alpha", feature);
        LinkedHashMap<?, ?> convertedValue = mapper.convertValue(featureSetExpected, LinkedHashMap.class);

        assertEquals(convertedValue, propertySource.getProperty(FEATURE_MANAGEMENT_KEY));
    }

    @Test
    public void testFeatureFlagThrowError() throws IOException {
        FeatureSet featureSet = new FeatureSet();
        try {
            propertySource.initProperties(featureSet);
        } catch (IOException e) {
            assertEquals("Found Feature Flag /foo/test_key_1 with invalid Content Type of ", e.getMessage());
        }
    }

    @Test
    public void testFeatureFlagBuildError() throws ServerException {
        when(clientStoreMock.listSettings(Mockito.any(), Mockito.anyString())).thenReturn(FEATURE_ITEMS);

        FeatureSet featureSet = new FeatureSet();
        try {
            propertySource.initProperties(featureSet);
        } catch (IOException e) {
            fail();
        }
        propertySource.initFeatures(featureSet);

        FeatureSet featureSetExpected = new FeatureSet();
        Feature feature = new Feature();
        feature.setKey("Alpha");
        ArrayList<FeatureFilterEvaluationContext> filters = new ArrayList<FeatureFilterEvaluationContext>();
        FeatureFilterEvaluationContext ffec = new FeatureFilterEvaluationContext();
        ffec.setName("TestFilter");
        filters.add(ffec);
        feature.setEnabledFor(filters);
        featureSetExpected.addFeature("Alpha", feature);
        LinkedHashMap<?, ?> convertedValue = mapper.convertValue(featureSetExpected, LinkedHashMap.class);

        assertEquals(convertedValue, propertySource.getProperty(FEATURE_MANAGEMENT_KEY));
    }

    @Test
    public void initNullValidContentTypeTest() throws ServerException {
        ArrayList<ConfigurationSetting> items = new ArrayList<ConfigurationSetting>();
        items.add(item3Null);
        when(clientStoreMock.listSettings(Mockito.any(), Mockito.anyString())).thenReturn(items)
                .thenReturn(new ArrayList<ConfigurationSetting>());

        FeatureSet featureSet = new FeatureSet();
        try {
            propertySource.initProperties(featureSet);
        } catch (IOException e) {
            fail("Failed Reading in Feature Flags");
        }

        String[] keyNames = propertySource.getPropertyNames();
        String[] expectedKeyNames = items.stream()
                .map(t -> t.getKey().substring(TEST_CONTEXT.length())).toArray(String[]::new);

        assertThat(keyNames).containsExactlyInAnyOrder(expectedKeyNames);
    }

    @Test
    public void initNullInvalidContentTypeFeatureFlagTest() throws ServerException {
        ArrayList<ConfigurationSetting> items = new ArrayList<ConfigurationSetting>();
        items.add(featureItemNull);
        when(clientStoreMock.listSettings(Mockito.any(), Mockito.anyString()))
                .thenReturn(new ArrayList<ConfigurationSetting>()).thenReturn(items);

        FeatureSet featureSet = new FeatureSet();
        try {
            propertySource.initProperties(featureSet);
        } catch (IOException e) {

        }

        String[] keyNames = propertySource.getPropertyNames();
        String[] expectedKeyNames = {};

        assertThat(keyNames).containsExactlyInAnyOrder(expectedKeyNames);
    }
}
