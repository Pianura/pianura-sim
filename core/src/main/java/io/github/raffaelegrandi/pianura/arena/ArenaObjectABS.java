// Copyright (c) 2008-2026 Raffaele Grandi
// SPDX-License-Identifier: MIT

package io.github.raffaelegrandi.pianura.arena;

public abstract class ArenaObjectABS {
	
	protected ObjectID objectID;
	
	public ArenaObjectABS(ObjectID objID){
		objectID = objID;
	}
	
	public ObjectID getID(){ return objectID;}

}//end class
