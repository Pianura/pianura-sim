package io.github.raffaelegrandi.pianura.utils;

import javax.swing.*;

@SuppressWarnings("serial")
public class LogArea extends JTextArea{
	private boolean clear = true;
	
	public LogArea(String initString){
		super(initString);
		init();
	}
	
	public LogArea(String initString, int row, int col){
		super(initString,row,col);
		init();
	}

	/** Long lines wrap on words: no horizontal scroll bar. */
	private void init(){
		setEditable(false);
		setLineWrap(true);
		setWrapStyleWord(true);
	}
	
	public void addMSG(String msg){ 
		if (clear){ setText(""); clear = false; }
		append(getDocument().getLength() == 0 ? msg : "\n" + msg);
		this.setCaretPosition(this.getDocument().getLength());		
	}
	
	public void setClear(boolean choice){ clear = choice; }

}//end class
