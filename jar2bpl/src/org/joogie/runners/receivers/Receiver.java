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

/**
 * Output Receiver of a Runner
 * 
 * @author arlt
 */
public interface Receiver {

	/**
	 * Receives text from output
	 * 
	 * @param text
	 *            Text
	 */
	public void receive(String text);

	/**
	 * Begin of the reception
	 */
	public void onBegin();

	/**
	 * End of the reception
	 */
	public void onEnd();

}