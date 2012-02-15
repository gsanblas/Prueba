/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.invoice.notification;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.MINUTES;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.config.CatalogConfig;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.engine.dao.EntitlementSqlDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.notificationq.DefaultNotificationQueueService;
import com.ning.billing.util.notificationq.DummySqlTest;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.dao.NotificationSqlDao;

public class TestNextBillingDateNotifier {
    private static Logger log = LoggerFactory.getLogger(TestNextBillingDateNotifier.class);
	private Clock clock;
	private DefaultNextBillingDateNotifier notifier;
	private DummySqlTest dao;
	private Bus eventBus;
	private MysqlTestingHelper helper;
	private InvoiceListenerMock listener = new InvoiceListenerMock();

	private static final class InvoiceListenerMock extends InvoiceListener {
		int eventCount = 0;
		UUID latestSubscriptionId = null;

		public InvoiceListenerMock() {
			super(null, null, null, null, null);
		}
		

		@Override
		public void handleNextBillingDateEvent(UUID subscriptionId,
				DateTime eventDateTime) {
			eventCount++;
			latestSubscriptionId=subscriptionId;
		}
		
		public int getEventCount() {
			return eventCount;
		}
		
		public UUID getLatestSubscriptionId(){
			return latestSubscriptionId;
		}
		
	}
	
	private class MockEntitlementDao implements EntitlementDao {

		@Override
		public List<SubscriptionBundle> getSubscriptionBundleForAccount(
				UUID accountId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public SubscriptionBundle getSubscriptionBundleFromKey(String bundleKey) {
			throw new UnsupportedOperationException();
		}

		@Override
		public SubscriptionBundle getSubscriptionBundleFromId(UUID bundleId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public SubscriptionBundle createSubscriptionBundle(
				SubscriptionBundleData bundle) {
			throw new UnsupportedOperationException();

		}

		@Override
		public Subscription getSubscriptionFromId(UUID subscriptionId) {
			return new BrainDeadSubscription();

		}

		@Override
		public UUID getAccountIdFromSubscriptionId(UUID subscriptionId) {
			throw new UnsupportedOperationException();

		}

		@Override
		public Subscription getBaseSubscription(UUID bundleId) {
			throw new UnsupportedOperationException();

		}

		@Override
		public List<Subscription> getSubscriptions(UUID bundleId) {
			throw new UnsupportedOperationException();

		}

		@Override
		public List<Subscription> getSubscriptionsForKey(String bundleKey) {
			throw new UnsupportedOperationException();

		}

		@Override
		public void updateSubscription(SubscriptionData subscription) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void createNextPhaseEvent(UUID subscriptionId,
				EntitlementEvent nextPhase) {
			throw new UnsupportedOperationException();
		}

		@Override
		public EntitlementEvent getEventById(UUID eventId) {
			throw new UnsupportedOperationException();

		}

		@Override
		public List<EntitlementEvent> getEventsForSubscription(
				UUID subscriptionId) {
			throw new UnsupportedOperationException();

		}

		@Override
		public List<EntitlementEvent> getPendingEventsForSubscription(
				UUID subscriptionId) {
			throw new UnsupportedOperationException();

		}

		@Override
		public void createSubscription(SubscriptionData subscription,
				List<EntitlementEvent> initialEvents) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void cancelSubscription(UUID subscriptionId,
				EntitlementEvent cancelEvent) {
			throw new UnsupportedOperationException();

		}

		@Override
		public void uncancelSubscription(UUID subscriptionId,
				List<EntitlementEvent> uncancelEvents) {
			throw new UnsupportedOperationException();
	
		}

		@Override
		public void changePlan(UUID subscriptionId,
				List<EntitlementEvent> changeEvents) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void migrate(UUID acountId, AccountMigrationData data) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void undoMigration(UUID accountId) {
			throw new UnsupportedOperationException();
		}
		
	}
	
	@BeforeClass(groups={"setup"})
	public void setup() throws ServiceException, IOException, ClassNotFoundException, SQLException {
		//TestApiBase.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION,  new AbstractModule() {
			protected void configure() {
				 bind(Clock.class).to(ClockMock.class).asEagerSingleton();
				 bind(Bus.class).to(InMemoryBus.class).asEagerSingleton();
				 bind(NotificationQueueService.class).to(DefaultNotificationQueueService.class).asEagerSingleton();
				 final InvoiceConfig invoiceConfig = new ConfigurationObjectFactory(System.getProperties()).build(InvoiceConfig.class);
				 bind(InvoiceConfig.class).toInstance(invoiceConfig);
				 final CatalogConfig catalogConfig = new ConfigurationObjectFactory(System.getProperties()).build(CatalogConfig.class);
                 bind(CatalogConfig.class).toInstance(catalogConfig);
                 bind(CatalogService.class).to(DefaultCatalogService.class).asEagerSingleton();
                 final MysqlTestingHelper helper = new MysqlTestingHelper();
				 bind(MysqlTestingHelper.class).toInstance(helper);
				 IDBI dbi = helper.getDBI();
				 bind(IDBI.class).toInstance(dbi);
                 bind(EntitlementDao.class).to(EntitlementSqlDao.class).asEagerSingleton();
			}
        });

        clock = g.getInstance(Clock.class);
        IDBI dbi = g.getInstance(IDBI.class);
        dao = dbi.onDemand(DummySqlTest.class);
        eventBus = g.getInstance(Bus.class);
        helper = g.getInstance(MysqlTestingHelper.class);
        notifier = new DefaultNextBillingDateNotifier(g.getInstance(NotificationQueueService.class), eventBus, g.getInstance(InvoiceConfig.class), new MockEntitlementDao(), listener);
        startMysql();
	}

	private void startMysql() throws IOException, ClassNotFoundException, SQLException {
		final String ddl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));
		final String testDdl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/util/ddl_test.sql"));
		final String entitlementDdl = IOUtils.toString(NotificationSqlDao.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));
		helper.startMysql();
		helper.initDb(ddl);
		helper.initDb(testDdl);
        helper.initDb(entitlementDdl);
	}


	@Test(enabled=true, groups="slow")
	public void test() throws Exception {
		final UUID subscriptionId = new UUID(0L,1L);
		final DateTime now = new DateTime();
		final DateTime readyTime = now.plusMillis(2000);

		eventBus.start();
		notifier.initialize();
		notifier.start();

		dao.inTransaction(new Transaction<Void, DummySqlTest>() {
			@Override
			public Void inTransaction(DummySqlTest transactional,
					TransactionStatus status) throws Exception {

				notifier.insertNextBillingNotification(transactional, subscriptionId, readyTime);
				return null;
			}
		});

		// Move time in the future after the notification effectiveDate
		((ClockMock) clock).setDeltaFromReality(3000);


	    await().atMost(1, MINUTES).until(new Callable<Boolean>() {
	            @Override
	            public Boolean call() throws Exception {
	                return listener.getEventCount() == 1;
	            }
	        });

		Assert.assertEquals(listener.getEventCount(), 1);
		Assert.assertEquals(listener.getLatestSubscriptionId(), subscriptionId);
	}
}
