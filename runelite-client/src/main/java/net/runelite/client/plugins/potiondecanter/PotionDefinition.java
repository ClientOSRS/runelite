package net.runelite.client.plugins.potiondecanter;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
class PotionDefinition
{
	private final PotionType type;
	private final int doses;
	private final int itemId;
}
