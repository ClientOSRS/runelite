/*
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.underwateragilitylogger;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HintArrowType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Underwater chest logger",
	description = "Logs chest spawns to file",
	tags = {"agility", "thieving", "underwater", "logger"}
)
public class UnderwaterLoggerPlugin extends Plugin
{
	public static final File RUNELITE_DIR = new File(System.getProperty("user.home"), ".runelite");
	public static final File UNDERWATER_LOGS_DIR = new File(RUNELITE_DIR, "underwater-logs");
	private static final WorldArea UNDERWATER_AREA = new WorldArea(3712, 10240, 128, 80, 1);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	private PrintWriter pw;
	private int lastHintX;
	private int lastHintY;
	private boolean isInUnderwaterArea;
	private boolean isOnline;

	protected void startUp() throws Exception
	{
		UNDERWATER_LOGS_DIR.mkdirs();
		isInUnderwaterArea = false;
		lastHintX = -1;
		lastHintY = -1;
		pw = new PrintWriter(new FileWriter(new File(UNDERWATER_LOGS_DIR, "data.log"), true));

		clientThread.invokeLater(() ->
		{
			isOnline =
				client.getGameState() == GameState.LOGGED_IN ||
				client.getGameState() == GameState.LOADING;
			if (isOnline)
			{
				checkLocation();
			}
		});
	}

	protected void shutDown() throws Exception
	{
		if (isInUnderwaterArea)
		{
			onLeave();
		}

		isInUnderwaterArea = false;
		lastHintX = 0;
		lastHintY = 0;
		pw.close();
		pw = null;
	}

	private void logToFile(String message)
	{
		String s = System.currentTimeMillis() + ": " + client.getTickCount() + ": " + message;
		pw.println(s);
		pw.flush();
	}

	private void checkLocation()
	{
		WorldPoint playerWp = client.getLocalPlayer().getWorldLocation();
		boolean inArea = UNDERWATER_AREA.distanceTo(playerWp) == 0;

		if (inArea && !isInUnderwaterArea)
		{
			onEnter();
		}
		if (!inArea && isInUnderwaterArea)
		{
			onLeave();
		}
	}

	private void onLeave()
	{
		logToFile("left");
		lastHintX = -1;
		lastHintY = -1;
		isInUnderwaterArea = false;
	}

	private void onEnter()
	{
		logToFile("entered on world " + client.getWorld());
		lastHintX = -1;
		lastHintY = -1;
		isInUnderwaterArea = true;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState gs = event.getGameState();

		if (!isOnline && gs == GameState.LOGGED_IN)
		{
			isOnline = true;
			isInUnderwaterArea = false;
		}

		if (isOnline &&
			gs != GameState.LOGGED_IN &&
			gs != GameState.LOADING)
		{
			if (isInUnderwaterArea)
			{
				onLeave();
			}
			isOnline = false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		checkLocation();

		if (!isInUnderwaterArea)
		{
			return;
		}

		if (client.getHintArrowType() != HintArrowType.WORLD_POSITION)
		{
			return;
		}

		WorldPoint hintWp = client.getHintArrowPoint();
		if (hintWp.getX() == lastHintX && hintWp.getY() == lastHintY)
		{
			return;
		}

		logToFile("chest moved " + hintWp.getX() + "," + hintWp.getY());

		lastHintX = hintWp.getX();
		lastHintY = hintWp.getY();
	}
}
