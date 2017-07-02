/**
 * Copyright (C) 2017 Le Thinh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.lethinh.matterexpansion.tile;

import java.io.IOException;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;

import io.lethinh.matterexpansion.network.EligiblePacketBuffer;
import io.lethinh.matterexpansion.tile.inventory.PerpetualInventoryCrafting;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;

/**
 *
 * @author Le Thinh
 */
public abstract class GenericMachineCraftingTile extends GenericPowerTile
		implements ISidedInventory, ITickable {

	protected NonNullList<ItemStack> stacks;
	protected EnumFacing side;
	protected long ticks;
	protected boolean isActive;
	public int progress;
	public final PerpetualInventoryCrafting craftMatrix;

	public GenericMachineCraftingTile(int size, String name, int width, int height) {
		super(name);
		this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
		this.side = EnumFacing.NORTH;
		this.ticks = 0;
		this.isActive = false;
		this.progress = 0;
		this.craftMatrix = new PerpetualInventoryCrafting(this, width, height);
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		this.stacks = NonNullList.withSize(this.getSizeInventory() - this.craftMatrix.getSizeInventory(),
				ItemStack.EMPTY);

		final NBTTagList tagList = compound.getTagList("Items", 10);

		IntStream.range(0, tagList.tagCount()).forEach(i -> {
			final NBTTagCompound itemTags = tagList.getCompoundTagAt(i);
			final int index = itemTags.getByte("Slot") & 0xFF;

			if (index >= 0 && index < this.stacks.size()) {
				this.stacks.set(index, new ItemStack(itemTags));
			} else if (index >= 0 && index < this.getSizeInventory()) {
				this.craftMatrix.setInventorySlotContents(index - this.stacks.size(), new ItemStack(itemTags));
			}
		});

		this.side = EnumFacing.values()[compound.getByte("Side")];
		super.readFromNBT(compound);
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound compound) {
		final NBTTagList tagList = new NBTTagList();

		IntStream.range(0, this.stacks.size()).filter(i -> !this.stacks.get(i).isEmpty()).forEach(i -> {
			final ItemStack stack = this.stacks.get(i);

			if (!stack.isEmpty()) {
				final NBTTagCompound itemTags = new NBTTagCompound();
				itemTags.setByte("Slot", (byte) i);
				stack.writeToNBT(itemTags);
				tagList.appendTag(itemTags);
			}
		});

		IntStream.range(0, this.craftMatrix.getSizeInventory())
				.filter(i -> !this.craftMatrix.getStackInSlot(i).isEmpty()).forEach(i -> {
					final ItemStack stack = this.craftMatrix.getStackInSlot(i);
					final NBTTagCompound itemTags = new NBTTagCompound();
					itemTags.setByte("Slot", (byte) (i + this.stacks.size()));
					stack.writeToNBT(itemTags);
					tagList.appendTag(itemTags);
				});

		if (!tagList.hasNoTags()) {
			compound.setTag("Items", tagList);
		}

		compound.setByte("Side", (byte) this.side.ordinal());
		return super.writeToNBT(compound);
	}

	/* IInventory */
	@Override
	public void clear() {
		this.stacks.clear();
	}

	@Override
	public void closeInventory(EntityPlayer player) {
	}

	@Nonnull
	@Override
	public ItemStack decrStackSize(int index, int count) {
		if (index >= this.stacks.size())
			return this.craftMatrix.decrStackSize(index - this.craftMatrix.getSizeInventory(), count);

		if (!this.stacks.get(index).isEmpty()) {
			ItemStack itemStack;

			if (this.stacks.get(index).getCount() <= count) {
				itemStack = this.stacks.get(index);
				this.stacks.set(index, ItemStack.EMPTY);
				this.markDirty();
				return itemStack;
			} else {
				itemStack = this.stacks.get(index).splitStack(count);

				if (this.stacks.get(index).isEmpty()) {
					this.stacks.set(index, ItemStack.EMPTY);
				}

				this.markDirty();
				return itemStack;
			}
		}

		return ItemStack.EMPTY;
	}

	@Override
	public int getField(int data) {
		return 0;
	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public int getSizeInventory() {
		return this.stacks.size() + this.craftMatrix.getSizeInventory();
	}

	@Nonnull
	@Override
	public ItemStack getStackInSlot(int index) {
		if (index >= this.stacks.size())
			return this.craftMatrix.getStackInSlot(index - this.stacks.size());

		return this.stacks.get(index);
	}

	@Override
	public boolean isEmpty() {
		return this.stacks.stream().anyMatch(itemStack -> !itemStack.isEmpty());
	}

	@Override
	public boolean isItemValidForSlot(int index, @Nonnull ItemStack stack) {
		return false;
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer player) {
		return player.getDistanceSq(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D) <= 64.0D
				&& !this.isInvalid() && this.world.getTileEntity(this.pos) == this;
	}

	@Override
	public void openInventory(EntityPlayer player) {
	}

	@Nonnull
	@Override
	public ItemStack removeStackFromSlot(int index) {
		if (index >= this.stacks.size())
			return this.craftMatrix.removeStackFromSlot(index - this.stacks.size());

		if (!this.stacks.get(index).isEmpty()) {
			final ItemStack itemStack = this.stacks.get(index);
			this.stacks.set(index, ItemStack.EMPTY);
			return itemStack;
		}

		return ItemStack.EMPTY;
	}

	@Override
	public void setField(int data, int value) {
	}

	@Override
	public void setInventorySlotContents(int index, @Nonnull ItemStack stack) {
		if (index >= this.stacks.size()) {
			this.craftMatrix.setInventorySlotContents(index - this.stacks.size(), stack);
		}

		this.stacks.set(index, stack);
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public boolean hasCustomName() {
		return false;
	}

	/* ISidedInventory */
	@Override
	public int[] getSlotsForFace(EnumFacing side) {
		return new int[0];
	}

	@Override
	public boolean canInsertItem(int index, @Nonnull ItemStack stack, EnumFacing side) {
		return !stack.isEmpty();
	}

	@Override
	public boolean canExtractItem(int index, @Nonnull ItemStack stack, EnumFacing side) {
		return stack.isEmpty();
	}

	/* ITickable */
	@Override
	public void update() {
		if (this.world.isRemote) {
			this.doServerWork();
		}

		if (this.canWork()) {
			if (!this.isActive)
				return;

			this.doClientWork();
			this.isActive = true;
		} else if (this.isActive) {
			this.stopWorking();
			this.isActive = false;
		}
	}

	/* WORK */
	/**
	 * Called when the tile wants to check if it can work.
	 *
	 * @return true if the tile can work otherwise false.
	 */
	protected abstract boolean canWork();

	/**
	 * Called when the tile works in server-side.
	 */
	protected void doServerWork() {
		this.ticks++;
	}

	/**
	 * Called when the tile works in client-side. And checked with the
	 * {@link GenericMachineTile#canWork} if it can work.
	 */
	protected abstract void doClientWork();

	/**
	 * Called when the tile stops working. And do something when it stopped
	 * (reset the timer, set the block state, ...)
	 */
	protected abstract void stopWorking();

	/* PACKET */
	@Override
	public void loadBlobsTickets(EligiblePacketBuffer packet) throws IOException {
		super.loadBlobsTickets(packet);
		this.stacks = packet.readItemStacks();
		this.side = packet.readSide();
	}

	@Override
	public void saveBlobsTickets(EligiblePacketBuffer packet) throws IOException {
		super.saveBlobsTickets(packet);
		packet.writeItemStacks(this.stacks);
		packet.writeSide(this.side);
	}

}
