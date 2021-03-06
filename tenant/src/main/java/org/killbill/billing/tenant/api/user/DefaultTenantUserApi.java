/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.tenant.api.user;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.tenant.api.DefaultTenant;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantData;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.tenant.dao.TenantDao;
import org.killbill.billing.tenant.dao.TenantModelDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.callcontext.TenantContext;

import com.google.inject.Inject;

public class DefaultTenantUserApi implements TenantUserApi {

    private final TenantDao tenantDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultTenantUserApi(final TenantDao tenantDao, final InternalCallContextFactory internalCallContextFactory) {
        this.tenantDao = tenantDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Tenant createTenant(final TenantData data, final CallContext context) throws TenantApiException {
        final Tenant tenant = new DefaultTenant(data);

        try {
            tenantDao.create(new TenantModelDao(tenant), internalCallContextFactory.createInternalCallContext(context));
        } catch (final TenantApiException e) {
            throw new TenantApiException(e, ErrorCode.TENANT_CREATION_FAILED);
        }

        return tenant;
    }

    @Override
    public Tenant getTenantByApiKey(final String key) throws TenantApiException {
        final TenantModelDao tenant = tenantDao.getTenantByApiKey(key);
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_API_KEY, key);
        }
        return new DefaultTenant(tenant);
    }

    @Override
    public Tenant getTenantById(final UUID id) throws TenantApiException {
        // TODO - API cleanup?
        final TenantModelDao tenant = tenantDao.getById(id, new InternalTenantContext(null, null));
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID, id);
        }
        return new DefaultTenant(tenant);
    }

    @Override
    public List<String> getTenantValueForKey(final String key, final TenantContext context)
            throws TenantApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        return tenantDao.getTenantValueForKey(key, internalContext);
    }

    @Override
    public void addTenantKeyValue(final String key, final String value, final CallContext context)
            throws TenantApiException {

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(context);
        // TODO Figure out the exact verification if nay
        /*
        final Tenant tenant = tenantDao.getById(callcontext.getTenantId(), internalContext);
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID, tenantId);
        }
        */
        tenantDao.addTenantKeyValue(key, value, internalContext);
    }

    @Override
    public void deleteTenantKey(final String key, final CallContext context)
            throws TenantApiException {
        /*
        final Tenant tenant = tenantDao.getById(tenantId, new InternalTenantContext(null, null));
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_ID, tenantId);
        }
        */
        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContext(context);
        tenantDao.deleteTenantKey(key, internalContext);
    }
}
