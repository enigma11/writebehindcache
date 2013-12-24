package com.gdiama.cache;

import org.aeonbits.owner.Config;

import java.util.concurrent.TimeUnit;

/*
    To instaniate: ConfigFactory.create(AppConfiguration.class);
 */

@Config.LoadPolicy(value = Config.LoadType.FIRST)
@Config.Sources({"file:${application.configurationFile}" })
public interface AppConfiguration extends Config {


    @DefaultValue("1000")
    long initDelayWriteBehindCacheInMillis();

    @DefaultValue("10000")
    long writeBehindFlushInterval();

    @DefaultValue("MILLISECONDS")
    TimeUnit writeBehindFlushTimeUnit();

    @DefaultValue("60000")
    long awaitTerminationDuration();

    @DefaultValue("MILLISECONDS")
    TimeUnit awaitTerminationTimeUnit();

}
