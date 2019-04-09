/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl;

import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.N;
import static com.amazonaws.services.dynamodbv2.model.ScalarAttributeType.S;
import static com.salesforce.dynamodbv2.mt.mappers.index.DynamoSecondaryIndex.DynamoSecondaryIndexType.GSI;
import static com.salesforce.dynamodbv2.mt.mappers.index.DynamoSecondaryIndex.DynamoSecondaryIndexType.LSI;
import static com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl.FieldMapping.IndexType.SECONDARYINDEX;
import static com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl.FieldMapping.IndexType.TABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.salesforce.dynamodbv2.mt.mappers.CreateTableRequestBuilder;
import com.salesforce.dynamodbv2.mt.mappers.MappingException;
import com.salesforce.dynamodbv2.mt.mappers.index.DynamoSecondaryIndex;
import com.salesforce.dynamodbv2.mt.mappers.index.DynamoSecondaryIndex.DynamoSecondaryIndexType;
import com.salesforce.dynamodbv2.mt.mappers.index.DynamoSecondaryIndexMapper;
import com.salesforce.dynamodbv2.mt.mappers.index.DynamoSecondaryIndexMapperByTypeImpl;
import com.salesforce.dynamodbv2.mt.mappers.metadata.DynamoTableDescription;
import com.salesforce.dynamodbv2.mt.mappers.metadata.DynamoTableDescriptionImpl;
import com.salesforce.dynamodbv2.mt.mappers.metadata.PrimaryKey;
import com.salesforce.dynamodbv2.mt.mappers.sharedtable.impl.FieldMapping.Field;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * TODO: write Javadoc.
 *
 * @author msgroi
 */
class TableMappingTest {

    private static final char DELIMITER = '.';
    private final DynamoTableDescription virtualTable = new DynamoTableDescriptionImpl(
            buildDefaultCreateTableRequestBuilderWithGsi().build());
    private final DynamoTableDescription physicalTable = new DynamoTableDescriptionImpl(CreateTableRequestBuilder
            .builder()
            .withTableName("physicalTableName")
            .withTableKeySchema("physicalhk", S, "physicalrk", N)
            .addSi("physicalgsi",
                    GSI,
                    new PrimaryKey("physicalgsihk", S, "physicalgsirk", N),
                    1L)
            .build());
    private final TableMapping sut = new TableMapping(virtualTable,
            new SingletonCreateTableRequestFactory(physicalTable.getCreateTableRequest()),
            new DynamoSecondaryIndexMapperByTypeImpl(),
            null,
            DELIMITER
    );
    private final Map<String, List<FieldMapping>> virtualToPhysicalFieldMappings = ImmutableMap.of(
            "virtualhk", ImmutableList.of(
                    new FieldMapping(new Field("virtualhk", S),
                            new Field("physicalhk", S),
                            "virtualTableName",
                            "physicalTableName",
                            TABLE,
                            true)),
            "virtualrk", ImmutableList.of(
                    new FieldMapping(new Field("virtualrk", N),
                            new Field("physicalrk", N),
                            "virtualTableName",
                            "physicalTableName",
                            TABLE,
                            false)),
            "virtualgsihk", ImmutableList.of(new FieldMapping(new Field("virtualgsihk", S),
                    new Field("physicalgsihk", S),
                    "virtualgsi",
                    "physicalgsi",
                    SECONDARYINDEX,
                    true)),
            "virtualgsirk", ImmutableList.of(new FieldMapping(new Field("virtualgsirk", N),
                    new Field("physicalgsirk", N),
                    "virtualgsi",
                    "physicalgsi",
                    SECONDARYINDEX,
                    false)));

    @Test
    void getVirtualTable() {
        assertEquals(virtualTable, sut.getVirtualTable());
    }

    @Test
    void getPhysicalTable() {
        assertEquals(physicalTable, sut.getPhysicalTable());
    }

    @Test
    void getItemMapper() {
        assertNotNull(sut.getItemMapper());
    }

    @Test
    void getQueryMapper() {
        assertNotNull(sut.getQueryAndScanMapper());
    }

    @Test
    void getAllVirtualToPhysicalFieldMappings() {
        assertEquals(virtualToPhysicalFieldMappings, sut.getAllVirtualToPhysicalFieldMappings());
    }

    @Test
    void getAllVirtualToPhysicalFieldMappingsDeduped() {
        TableMapping sut = new TableMapping(new DynamoTableDescriptionImpl(
                buildDefaultCreateTableRequestBuilderWithGsi()
                .addSi("virtuallsi",
                        LSI,
                        new PrimaryKey("virtualhk", S, "virtualgsirk", N),
                        1L)
                .build()),
            new SingletonCreateTableRequestFactory(new DynamoTableDescriptionImpl(CreateTableRequestBuilder
                .builder()
                .withTableName("physicalTableName")
                .withTableKeySchema("physicalhk", S, "physicalrk", N)
                .addSi("physicalgsi",
                    GSI,
                    new PrimaryKey("physicalgsihk", S, "physicalgsirk", N),
                    1L)
                .addSi("virtuallsi",
                    LSI,
                    new PrimaryKey("physicalhk", S, "physicallsirk", N),
                    1L)
                .build()).getCreateTableRequest()),
            new DynamoSecondaryIndexMapperByTypeImpl(),
            null,
            DELIMITER
        );
        Map<String, List<FieldMapping>> fieldMappings = sut.getAllVirtualToPhysicalFieldMappings();
        Map<String, Integer> expectedMappingCounts = ImmutableMap.of("virtualhk", 2,
            "virtualrk", 1,
            "virtualgsihk", 1,
            "virtualgsirk", 2);
        assertEquals(4, expectedMappingCounts.keySet().size());
        expectedMappingCounts.forEach((key, expectedCount) ->
            assertEquals(expectedCount.intValue(), fieldMappings.get(key).size(), "key=" + key));
        Set<String> expectedMappingCountsDeduped =
                ImmutableSet.of("virtualhk", "virtualrk", "virtualgsihk", "virtualgsirk");
        assertEquals(expectedMappingCountsDeduped, sut.getAllVirtualToPhysicalFieldMappingsDeduped().keySet());
    }

    @Test
    void getIndexPrimaryKeyFieldMappings() {
        assertEquals(virtualToPhysicalFieldMappings.entrySet().stream()
                        .filter(fieldMappingEntry -> fieldMappingEntry.getKey().contains("gsi"))
                        .flatMap((Function<Entry<String, List<FieldMapping>>, Stream<FieldMapping>>)
                            fieldMappingEntry -> fieldMappingEntry.getValue().stream())
                        .collect(Collectors.toList()),
                sut.getIndexPrimaryKeyFieldMappings(virtualTable.getGsi("virtualgsi").get()));
    }

    @Test
    void buildVirtualToPhysicalKeyFieldMappings() {
        assertEquals(virtualToPhysicalFieldMappings.entrySet().stream().filter(entry
            -> ImmutableSet.of("virtualhk", "virtualrk").contains(entry.getKey()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)),
                sut.buildVirtualToPhysicalKeyFieldMappings());
    }

    @Test
    void validateVirtualPhysicalCompatibility_missingVirtualHk() {
        assertException((TestFunction<NullPointerException>) () ->
                        sut.validateCompatiblePrimaryKey(new PrimaryKey(null, S), new PrimaryKey("hk", N)),
                "hash key is required on virtual table");
    }

    @Test
    void validateVirtualPhysicalCompatibility_missingPhysicalHk() {
        assertException((TestFunction<NullPointerException>) () ->
                        sut.validateCompatiblePrimaryKey(new PrimaryKey("hk", S), new PrimaryKey(null, S)),
                "hash key is required on physical table");
    }

    @Test
    void validateVirtualPhysicalCompatibility_invalidVirtualHkType() {
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validateCompatiblePrimaryKey(new PrimaryKey("hk", S), new PrimaryKey("hk", N)),
                "hash key must be of type S");
    }

    @Test
    void validateVirtualPhysicalCompatibility_physicalRkMissing() {
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validateCompatiblePrimaryKey(new PrimaryKey("hk", S, "rk", S),
                                new PrimaryKey("hk", S)),
                "rangeKey exists on virtual primary key but not on physical");
    }

    @Test
    void validateVirtualPhysicalCompatibility_incompatibleRkTypes() {
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validateCompatiblePrimaryKey(new PrimaryKey("hk", S, "rk", S),
                                new PrimaryKey("hk", S, "rk", N)),
                "virtual and physical range-key types mismatch");
    }

    @Test
    void validateSecondaryIndexMapping() {
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validateCompatiblePrimaryKey(new PrimaryKey("hk", S, "rk", S),
                                new PrimaryKey("hk", S, "rk", N)),
                "virtual and physical range-key types mismatch");
    }

    @Test
    void validateSecondaryIndexes_lookupFailure() throws MappingException {
        DynamoSecondaryIndexMapper spyIndexMapper = spy(DynamoSecondaryIndexMapperByTypeImpl.class);
        TableMapping tableMapping = new TableMapping(virtualTable,
                new SingletonCreateTableRequestFactory(physicalTable.getCreateTableRequest()),
                spyIndexMapper,
                null,
                DELIMITER
        );
        when(spyIndexMapper.lookupPhysicalSecondaryIndex(virtualTable.getSis().get(0), physicalTable))
                .thenThrow(new IllegalArgumentException("index mapping exception"));
        assertException((TestFunction<IllegalArgumentException>) () ->
                        tableMapping.validateSecondaryIndexes(virtualTable, physicalTable, spyIndexMapper),
                "index mapping exception");
    }

    @Test
    void validateSecondaryIndexes_incompatiblePrimaryKey() {
        DynamoTableDescription mockVirtualTable = mock(DynamoTableDescription.class);
        DynamoSecondaryIndex secondaryIndex = mock(DynamoSecondaryIndex.class);
        when(secondaryIndex.getType()).thenReturn(GSI);
        when(secondaryIndex.getPrimaryKey()).thenThrow(new IllegalArgumentException("incompatible index mapping"));
        when(mockVirtualTable.getSis()).thenReturn(ImmutableList.of(secondaryIndex));
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validateSecondaryIndexes(mockVirtualTable,
                                physicalTable,
                                new DynamoSecondaryIndexMapperByTypeImpl()),
                "incompatible index mapping");
    }

    @Test
    void validatePhysicalTable() {
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validatePhysicalTable(new DynamoTableDescriptionImpl(
                                CreateTableRequestBuilder
                                        .builder()
                                        .withTableName("physicalTableName")
                                        .withTableKeySchema("physicalhk", N)
                                        .build())),
                "physical table physicalTableName's primary-key hash key must be type S, encountered type N");
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validatePhysicalTable(new DynamoTableDescriptionImpl(
                                CreateTableRequestBuilder
                                        .builder()
                                        .withTableName("physicalTableName")
                                        .withTableKeySchema("physicalhk", S)
                                        .addSi("physicalgsi",
                                                GSI,
                                                new PrimaryKey("physicalgsihk", N),
                                                1L)
                                        .build())),
                "physical table physicalTableName's GSI physicalgsi's primary-key hash key must be type S, encountered "
                        + "type N");
        assertException((TestFunction<IllegalArgumentException>) () ->
                        sut.validatePhysicalTable(new DynamoTableDescriptionImpl(
                                CreateTableRequestBuilder
                                        .builder()
                                        .withTableName("physicalTableName")
                                        .withTableKeySchema("physicalhk", S)
                                        .addSi("physicallsi", LSI, new PrimaryKey("physicalgsihk", N),
                                                1L)
                                        .build())),
                "physical table physicalTableName's LSI physicallsi's primary-key hash key must be type S, encountered "
                        + "type N");
    }

    @Test
    void multipleVirtualGsisMappedToSinglePhysical() {
        assertException(() -> testSecondaryIndex(GSI), "Duplicate key virtualsi");
    }

    @Test
    void multipleVirtualLsisMappedToSinglePhysical() {
        assertException(() -> testSecondaryIndex(LSI), "Duplicate key virtualsi");
    }

    void testSecondaryIndex(DynamoSecondaryIndexType secondaryIndexType) {
        sut.validateSecondaryIndexes(
            new DynamoTableDescriptionImpl(buildDefaultCreateTableRequestBuilder()
                .addSi("virtualsi1",
                    secondaryIndexType, new PrimaryKey("hk", S, "rk", N), 0L)
                .addSi("virtualsi2",
                    secondaryIndexType, new PrimaryKey("hk", S, "rk", N), 0L)
                .build()),
            new DynamoTableDescriptionImpl(buildDefaultCreateTableRequestBuilder()
                .addSi("virtualsi",
                    secondaryIndexType, new PrimaryKey("hk", S, "rk", N), 0L)
                .build()),
            new DynamoSecondaryIndexMapperByTypeImpl()
        );
    }

    private static void assertException(TestFunction test, String expectedMessagePrefix) {
        try {
            test.run();
            fail("Expected exception '" + expectedMessagePrefix + "' not encountered");
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
            assertTrue(e.getMessage().startsWith(expectedMessagePrefix),
                    "expectedPrefix=" + expectedMessagePrefix + ", actual=" + e.getMessage());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static CreateTableRequestBuilder buildDefaultCreateTableRequestBuilder() {
        return CreateTableRequestBuilder
                .builder()
                .withTableName("virtualTableName")
                .withTableKeySchema("virtualhk", S, "virtualrk", N);
    }

    private static CreateTableRequestBuilder buildDefaultCreateTableRequestBuilderWithGsi() {
        return buildDefaultCreateTableRequestBuilder()
                .addSi("virtualgsi",
                        GSI,
                        new PrimaryKey("virtualgsihk", S, "virtualgsirk", N),
                        1L);
    }

    private interface TestFunction<T extends Throwable> {

        void run() throws T;
    }

}