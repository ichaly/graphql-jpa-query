/*
 * Copyright 2017 IntroPro Ventures Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.introproventures.graphql.jpa.query.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.introproventures.graphql.jpa.query.converter.model.JsonEntity;
import com.introproventures.graphql.jpa.query.converter.model.VariableValue;
import com.introproventures.graphql.jpa.query.schema.GraphQLExecutor;
import com.introproventures.graphql.jpa.query.schema.GraphQLSchemaBuilder;
import com.introproventures.graphql.jpa.query.schema.JavaScalars;
import com.introproventures.graphql.jpa.query.schema.JavaScalars.GraphQLObjectCoercing;
import com.introproventures.graphql.jpa.query.schema.impl.GraphQLJpaExecutor;
import com.introproventures.graphql.jpa.query.schema.impl.GraphQLJpaSchemaBuilder;
import graphql.schema.GraphQLScalarType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment=WebEnvironment.NONE, 
                properties = "spring.datasource.data=GraphQLJpaConverterTests.sql")
@TestPropertySource({"classpath:hibernate.properties"})
public class GraphQLJpaConverterTests {
    
    @SpringBootApplication
    static class Application {
        @Bean
        public GraphQLExecutor graphQLExecutor(final GraphQLSchemaBuilder graphQLSchemaBuilder) {
            return new GraphQLJpaExecutor(graphQLSchemaBuilder.build());
        }

        @Bean
        public GraphQLSchemaBuilder graphQLSchemaBuilder(final EntityManager entityManager) {
            
            JavaScalars.register(JsonNode.class, new GraphQLScalarType("Json", "Json type", new GraphQLObjectCoercing()));    
            JavaScalars.register(VariableValue.class, new GraphQLScalarType("VariableValue", "VariableValue Type", new GraphQLObjectCoercing()));    
            
            return new GraphQLJpaSchemaBuilder(entityManager)
                .name("HashMapSchema")
                .description("Json Entity test schema");
        }
        
    }
    
    @Autowired
    private GraphQLExecutor executor;
    
    @Autowired
    private EntityManager entityManager;
    
    @Test
    public void contextLoads() {
        
    }
    
    @Test
    @Transactional
    public void queryTester() {
        // given:
        Query query = entityManager.createQuery("select json from JsonEntity json where json.attributes LIKE '%key%'");

        // when:
        List<?> result = query.getResultList();

        // then:
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
    }
    
    @Test
    @Transactional
    public void criteriaTester() {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<JsonEntity> criteria = builder.createQuery(JsonEntity.class);
        Root<JsonEntity> json = criteria.from(JsonEntity.class);

        JsonNode value = new ObjectMapper().valueToTree(Collections.singletonMap("attr",
                                                                                 new String[] {"1","2","3","4","5"}));
        criteria.select(json)
                .where(builder.equal(json.get("attributes"), value));
        
        // when:
        List<?> result = entityManager.createQuery(criteria).getResultList();

        // then:
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
    }
    
    @Test // Problem with generating cast() in the where expression
    @Transactional
    public void criteriaTesterLike() {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<JsonEntity> criteria = builder.createQuery(JsonEntity.class);
        Root<JsonEntity> json = criteria.from(JsonEntity.class);

        criteria.select(json)
                .where(builder.like(json.get("attributes").as(String.class), "%key%"));
        
        // when:
        List<?> result = entityManager.createQuery(criteria).getResultList();

        // then:
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
    }    
    

    @Test
    @Transactional
    public void criteriaTesterLocate() {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<JsonEntity> criteria = builder.createQuery(JsonEntity.class);
        Root<JsonEntity> json = criteria.from(JsonEntity.class);

        criteria.select(json)
                .where(builder.gt(builder.locate(json.<String>get("attributes"),"key"), 0));
        
        // when:
        List<?> result = entityManager.createQuery(criteria).getResultList();

        // then: 
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(1);
    }
    
    @Test
    public void queryJsonEntity() {
        //given
        String query = "query {" + 
                "  JsonEntities {" + 
                "    select {" + 
                "      id" + 
                "      firstName" + 
                "      lastName" + 
                "      attributes" + 
                "    }" + 
                "  }" + 
                "}";
        
        String expected = "{JsonEntities={select=["
                + "{id=1, firstName=john, lastName=doe, attributes={\"attr\":{\"key\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}}}, "
                + "{id=2, firstName=joe, lastName=smith, attributes={\"attr\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}}"
                + "]}}";

        //when
        Object result = executor.execute(query).getData();

        // then
        assertThat(result.toString()).isEqualTo(expected);
    }     
    
    @Test
    public void queryJsonEntityWhereSearchCriteria() {
        //given
        String query = "query {" + 
                "  JsonEntities(where: {" 
                +     "attributes: {LOCATE: \"key\"}" 
                + "}) {" + 
                "    select {" + 
                "      id" + 
                "      firstName" + 
                "      lastName" + 
                "      attributes" + 
                "    }" + 
                "  }" + 
                "}";
        
        String expected = "{JsonEntities={select=["
                + "{id=1, firstName=john, lastName=doe, attributes={\"attr\":{\"key\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}}}"
                + "]}}";

        //when
        Object result = executor.execute(query).getData();

        // then
        assertThat(result.toString()).isEqualTo(expected);
    }
    
    @Test
    public void queryTaskVariablesWhereSearchCriteria() {
        //given
        String query = "query {" + 
                "  TaskVariables(where: {" 
                +     "value: {LOCATE: \"true\"}" 
                + "}) {" + 
                "    select {" + 
                "      id" + 
                "      name" + 
                "      value" + 
                "    }" + 
                "  }" + 
                "}";
        
        String expected = "{TaskVariables={select=[{id=2, name=variable2, value=true}]}}";

        //when
        Object result = executor.execute(query).getData();

        // then
        assertThat(result.toString()).isEqualTo(expected);
    }
    
    @Test
    public void queryTaskVariablesWhereSearchCriteriaVariableBinding() {
        //given
        String query = "query($value: VariableValue!) {" + 
                "  TaskVariables(where: {" 
                +     "value: {LOCATE: $value }" 
                + "}) {" + 
                "    select {" + 
                "      id" + 
                "      name" + 
                "      value" + 
                "    }" + 
                "  }" + 
                "}";
        
        Map<String,Object> variables = Collections.singletonMap("value", true);
        
        String expected = "{TaskVariables={select=[{id=2, name=variable2, value=true}]}}";

        //when
        Object result = executor.execute(query, variables).getData();

        // then
        assertThat(result.toString()).isEqualTo(expected);
    }
    

    @Test
    public void queryProcessVariablesWhereSearchCriteriaVariableBindings() {
        //given
        String query = "query($value: VariableValue!)  {" + 
                " ProcessVariables(where: {" 
                +     "value: {LOCATE: $value}" 
                + "}) {" + 
                "    select {" + 
                "      id" + 
                "      name" + 
                "      value" + 
                "    }" + 
                "  }" + 
                "}";

        Map<String,Object> variables = Collections.singletonMap("value", "[\"1\",\"2\",\"3\",\"4\",\"5\"]");
        
        String expected = "{ProcessVariables={select=[{id=1, name=document, value={key=[1, 2, 3, 4, 5]}}]}}";

        //when
        Object result = executor.execute(query, variables).getData();

        // then
        assertThat(result.toString()).isEqualTo(expected);
    }

    @Test
    public void queryProcessVariablesWhereSearchCriteria() {
        //given
        String query = "query {" + 
                " ProcessVariables(where: {" 
                +     "value: {LOCATE: \"[\\\"1\\\",\\\"2\\\",\\\"3\\\",\\\"4\\\",\\\"5\\\"]\"}" 
                + "}) {" + 
                "    select {" + 
                "      id" + 
                "      name" + 
                "      value" + 
                "    }" + 
                "  }" + 
                "}";
        
        String expected = "{ProcessVariables={select=[{id=1, name=document, value={key=[1, 2, 3, 4, 5]}}]}}";

        //when
        Object result = executor.execute(query).getData();

        // then
        assertThat(result.toString()).isEqualTo(expected);
    }
    
}