/*
 * jimple2boogie - Translates Jimple (or Java) Programs to Boogie
 * Copyright (C) 2013 Martin Schaef and Stephan Arlt
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.joogie.runners.receivers;

import java.io.FileWriter;
import java.io.IOException;

/**
 * File Output Receiver of a Runner
 * 
 * @author schaef
 */
public class FileReceiver implements Receiver {

	/**
	 * FileWriter
	 */
	private FileWriter fileWriter;

	/**
	 * C-tor
	 * 
	 * @param fileWriter
	 *            FileWriter
	 */
	public FileReceiver(FileWriter fileWriter) {
		this.fileWriter = fileWriter;
	}

	@Override
	public void receive(String text) {
		try {
			fileWriter.write(text);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onBegin() {
		// do nothing
	}

	@Override
	public void onEnd() {
		try {
			fileWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
