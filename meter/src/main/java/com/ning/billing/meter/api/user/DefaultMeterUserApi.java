/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.meter.api.user;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTime;

import com.ning.billing.ObjectType;
import com.ning.billing.meter.api.MeterUserApi;
import com.ning.billing.meter.timeline.TimelineEventHandler;
import com.ning.billing.meter.timeline.persistent.TimelineDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

public class DefaultMeterUserApi implements MeterUserApi {

    private static final String AGGREGATE_METRIC_NAME = "__AGGREGATE__";

    private final TimelineEventHandler timelineEventHandler;
    private final TimelineDao timelineDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultMeterUserApi(final TimelineEventHandler timelineEventHandler,
                               final TimelineDao timelineDao,
                               final InternalCallContextFactory internalCallContextFactory) {
        this.timelineEventHandler = timelineEventHandler;
        this.timelineDao = timelineDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void getAggregateUsage(final OutputStream outputStream, final UUID bundleId, final Collection<String> categories,
                                  final DateTime fromTimestamp, final DateTime toTimestamp, final TenantContext context) throws IOException {
        final ImmutableMap.Builder<String, Collection<String>> metricsPerCategory = new Builder<String, Collection<String>>();
        for (final String category : categories) {
            metricsPerCategory.put(category, ImmutableList.<String>of(AGGREGATE_METRIC_NAME));
        }
        getUsage(outputStream, bundleId, metricsPerCategory.build(), fromTimestamp, toTimestamp, context);
    }

    @Override
    public void getUsage(final OutputStream outputStream, final UUID bundleId, final Map<String, Collection<String>> metricsPerCategory,
                         final DateTime fromTimestamp, final DateTime toTimestamp, final TenantContext context) throws IOException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);

        final JsonSamplesOutputer outputerJson = new JsonSamplesOutputer(timelineEventHandler, timelineDao, internalTenantContext);
        outputerJson.output(outputStream, ImmutableList.<UUID>of(bundleId), metricsPerCategory, fromTimestamp, toTimestamp);
    }

    @Override
    public void incrementUsage(final UUID bundleId, final String categoryName, final String metricName,
                               final DateTime timestamp, final CallContext context) {
        incrementUsage(bundleId,
                       ImmutableMap.<String, Map<String, Object>>of(categoryName, ImmutableMap.<String, Object>of(metricName, (short) 1)),
                       timestamp,
                       context);
    }

    @Override
    public void incrementUsageAndAggregate(final UUID bundleId, final String categoryName, final String metricName,
                                           final DateTime timestamp, final CallContext context) {
        incrementUsage(bundleId,
                       ImmutableMap.<String, Map<String, Object>>of(categoryName, ImmutableMap.<String, Object>of(metricName, (short) 1, AGGREGATE_METRIC_NAME, (short) 1)),
                       timestamp,
                       context);
    }

    @Override
    public void incrementUsage(final UUID bundleId, final Map<String, Map<String, Object>> samplesForCategoriesAndMetrics,
                               final DateTime timestamp, final CallContext context) {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(bundleId, ObjectType.BUNDLE, context);

        for (final String category : samplesForCategoriesAndMetrics.keySet()) {
            final String sourceName = bundleId.toString();
            timelineEventHandler.record(sourceName, category, timestamp, samplesForCategoriesAndMetrics.get(category),
                                        internalCallContext);
        }
    }
}
