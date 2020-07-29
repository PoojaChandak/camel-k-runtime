/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.kamelet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.event.CamelContextInitializedEvent;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.service.ServiceHelper;

@UriEndpoint(
    firstVersion = "3.5.0",
    scheme = "kamelet",
    syntax = "kamelet:templateId/routeId",
    title = "Kamelet",
    label = "camel-k")
public class KameletEndpoint extends DefaultEndpoint {
    @Metadata(required = true)
    @UriPath(description = "The Route Template ID")
    private final String templateId;

    @Metadata(required = false)
    @UriPath(description = "The Route ID", defaultValueNote = "The ID will be auto-generated if not provided")
    private final String routeId;

    private final Map<String, Object> kameletProperties;
    private final String kameletUri;

    public KameletEndpoint(
            String uri,
            KameletComponent component,
            String templateId,
            String routeId,
            Map<String, Object> kameletProperties) {

        super(uri, component);

        this.templateId = templateId;
        this.routeId = routeId;
        this.kameletProperties = kameletProperties;
        this.kameletUri = "direct:" + routeId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getRouteId() {
        return routeId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KameletProducer();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new KemeletConsumer(processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // TODO: lets find a nicer way to do this
        EventNotifier notifier = new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                String id = getCamelContext().addRouteFromTemplate(routeId, templateId, kameletProperties);
                List<RouteDefinition> list = new ArrayList<>(1);
                list.add(getCamelContext().adapt(ModelCamelContext.class).getRouteDefinition(id));
                getCamelContext().adapt(ModelCamelContext.class).startRouteDefinitions(list);
                // no longer needed so we can remove ourselves
                getCamelContext().getManagementStrategy().removeEventNotifier(this);
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelContextInitializedEvent;
            }
        };

        ServiceHelper.startService(notifier);
        getCamelContext().getManagementStrategy().addEventNotifier(notifier);
    }

    // *********************************
    //
    // Helpers
    //
    // *********************************

    private class KemeletConsumer extends DefaultConsumer {
        private volatile Endpoint endpoint;
        private volatile Consumer consumer;

        public KemeletConsumer(Processor processor) {
            super(KameletEndpoint.this, processor);
        }

        @Override
        protected void doStart() throws Exception {
            endpoint = getCamelContext().getEndpoint(kameletUri);
            consumer = endpoint.createConsumer(getProcessor());

            ServiceHelper.startService(endpoint, consumer);
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopService(consumer, endpoint);
            super.doStop();
        }
    }

    private class KameletProducer extends DefaultAsyncProducer {
        private volatile Endpoint endpoint;
        private volatile AsyncProducer producer;

        public KameletProducer() {
            super(KameletEndpoint.this);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            if (producer != null) {
                return producer.process(exchange, callback);
            } else {
                callback.done(true);
                return true;
            }
        }

        @Override
        protected void doStart() throws Exception {
            endpoint = getCamelContext().getEndpoint(kameletUri);
            producer = endpoint.createAsyncProducer();
            ServiceHelper.startService(endpoint, producer);
            super.doStart();
        }

        @Override
        protected void doStop() throws Exception {
            ServiceHelper.stopService(producer, endpoint);
            super.doStop();
        }
    }
}