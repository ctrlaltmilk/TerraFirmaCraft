/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.util.mechanical;

import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

import net.dries007.tfc.common.capabilities.power.IRotator;
import net.dries007.tfc.common.capabilities.power.OldRotationCapability;
import net.dries007.tfc.util.Helpers;

public final class NetworkTracker
{

    public static void updateAllNetworkComponents(MechanicalNetwork network)
    {
        if (!network.valid)
        {
            return;
        }
        final IRotator source = network.source;
        final GraphResult result = populateGraph(source, source.levelOrThrow(), source.getBlockPos(), new LinkedList<>());
        if (result.valid)
        {
            result.rotators.forEach(network::add);
        }
    }

    public static void tickNetwork(MechanicalNetwork network)
    {
        if (network.members.isEmpty())
        {
            destroyNetwork(network);
            return;
        }
        final IRotator source = network.source;
        if (!network.valid)
        {
            destroyAndRecreateFromSource(network);
            return;
        }
        if (isLoaded(source))
        {
            if (source.getBlockEntity().isRemoved() || source.getSignal() == 0)
            {
                destroyNetwork(network);
                return;
            }
        }
        boolean destroy = false;
        for (IRotator rotator : network.members)
        {
            if (isLoaded(rotator))
            {
                if (rotator.getBlockEntity().isRemoved())
                {
                    destroy = true;
                    break;
                }
            }
        }
        if (destroy)
        {
            destroyAndRecreateFromSource(network);
        }
    }

    private static void destroyAndRecreateFromSource(MechanicalNetwork network)
    {
        destroyNetwork(network);
        if (isLoaded(network.source))
        {
            MechanicalUniverse.getOrCreate(network.source);
        }
    }

    private static boolean isLoaded(IRotator rotator)
    {
        return rotator.levelOrThrow().isLoaded(rotator.getBlockPos());
    }

    public static void destroyNetwork(MechanicalNetwork network)
    {
        for (IRotator rotator : network.members)
        {
            if (!rotator.getBlockEntity().isRemoved())
            {
                rotator.setSignal(0);
                rotator.setId(-1);
            }
        }
        network.valid = false;
        network.members.clear();
        MechanicalUniverse.delete(network.source);
    }

    public static GraphResult populateGraph(IRotator current, Level level, BlockPos pos, List<IRotator> members)
    {
        if (!members.contains(current))
        {
            members.add(current);
        }
        for (IRotator neighbor : getConnections(current, level, pos))
        {
            if (members.contains(neighbor))
            {
                continue;
            }
//            if (neighbor.isSource())
//            {
//                level.destroyBlock(neighbor.getBlockPos(), true);
//                return new GraphResult(List.of(), false);
//            }
            final int suppliedPower = current.getSignal();
            neighbor.setSignal(suppliedPower - 1);
            neighbor.setId(current.getId());
            members.add(neighbor);
            if (current.getSignal() > 0)
            {
                GraphResult graphResult = populateGraph(neighbor, neighbor.levelOrThrow(), neighbor.getBlockPos(), members);
                if (!graphResult.valid)
                {
                    return new GraphResult(List.of(), false);
                }
                members.addAll(graphResult.rotators);
            }
        }
        return new GraphResult(members, true);
    }

    private static List<IRotator> getConnections(IRotator current, Level level, BlockPos pos)
    {
        final List<IRotator> rotators = new LinkedList<>();
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Direction dir : Helpers.DIRECTIONS)
        {
            cursor.setWithOffset(pos, dir);
            final IRotator neighbor = getRotator(level, cursor);
            if (neighbor != null)
            {
                if (neighbor.hasShaft(level, cursor, dir.getOpposite()) && current.hasShaft(level, pos, dir))
                {
                    rotators.add(neighbor);
                }
            }
        }
        return rotators;
    }

    public record GraphResult(List<IRotator> rotators, boolean valid) {}

    public static void onNodeUpdated(IRotator rotator)
    {
        var net = rotator.getExistingNetwork();
        if (net != null)
        {
            net.valid = false;
        }
    }

    public static void onNodeAdded(IRotator rotator)
    {
        if (rotator.isClientSide()) return;
        var net = rotator.getExistingNetwork();
        if (net == null)
        {
            for (IRotator neighbor : getConnections(rotator, rotator.levelOrThrow(), rotator.getBlockPos()))
            {
                var otherNet = neighbor.getExistingNetwork();
                if (otherNet != null)
                {
                    destroyAndRecreateFromSource(otherNet);
                }
            }
        }
        if (rotator.isSource())
        {
            MechanicalUniverse.getOrCreate(rotator);
        }
    }

    @Nullable
    public static IRotator getRotator(LevelAccessor level, BlockPos pos)
    {
        var be = level.getBlockEntity(pos);
        if (be != null)
        {
            return be.getCapability(OldRotationCapability.ROTATION).resolve().orElse(null);
        }
        return null;
    }

}
