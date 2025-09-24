package net.runelite.client.plugins.autopvp;

import com.github.naton1.rl.env.nh.NhEnvironmentParams;
import java.util.Optional;

/**
 * Selection modes for choosing the naton NH loadout.
 */
public enum LoadoutOverride
{
    AUTO(null, "Auto Detect"),
    PURE(NhEnvironmentParams.AccountBuild.PURE, "Pure"),
    ZERKER(NhEnvironmentParams.AccountBuild.ZERKER, "Zerker"),
    MED(NhEnvironmentParams.AccountBuild.MED, "Med"),
    MAXED(NhEnvironmentParams.AccountBuild.MAXED, "Max"),
    LMS_PURE(NhEnvironmentParams.AccountBuild.LMS_PURE, "LMS Pure"),
    LMS_ZERKER(NhEnvironmentParams.AccountBuild.LMS_ZERKER, "LMS Zerker"),
    LMS_MED(NhEnvironmentParams.AccountBuild.LMS_MED, "LMS Med");

    private final NhEnvironmentParams.AccountBuild accountBuild;
    private final String displayName;

    LoadoutOverride(NhEnvironmentParams.AccountBuild accountBuild, String displayName)
    {
        this.accountBuild = accountBuild;
        this.displayName = displayName;
    }

    public Optional<NhEnvironmentParams.AccountBuild> getAccountBuild()
    {
        return Optional.ofNullable(accountBuild);
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
