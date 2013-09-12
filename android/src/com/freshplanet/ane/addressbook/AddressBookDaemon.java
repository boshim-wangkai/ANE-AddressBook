package com.freshplanet.ane.addressbook;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;

/**
 * The daemon is the part of the ane that queries the address book
 * it is run in a separate thread
 */
public class AddressBookDaemon implements Runnable { 
	
	private HashSet<String> contactCache ;
	private ContentResolver contentResolver ;
	private AddressBookDaemonCallback callback ;
	private int batchSize ;
	
	public AddressBookDaemon( HashSet<String> contactCache, int batchSize, ContentResolver contentResolver, AddressBookDaemonCallback callback ) {
		
		this.contactCache = contactCache ;
		this.contentResolver = contentResolver ;
		this.callback = callback ;
		this.batchSize = batchSize ;
		
	}
	
	@Override
	public void run() {
		try{// handle interruption
		
		List<String> newEntries = new LinkedList<String>() ;
			
		// parse phones
		Cursor contactCursor = this.contentResolver.query(
				Phone.CONTENT_URI, 
				new String[] { Phone.NORMALIZED_NUMBER, Phone.DISPLAY_NAME,  },
				null, null, null
			);
		
		// iterate over the cursor
		while( contactCursor.moveToNext() )
		{
			stopIfInterrupted() ;
			String phone = contactCursor.getString(0) ;
			String compositeName = contactCursor.getString(1);
			String phoneId = "phone_" + phone ;
			if( !contactCache.contains( phoneId ) )
			{
				newEntries.add( "\""+phoneId+"\":{\"firstName\":" + (compositeName != null ? ("\""+compositeName+"\"") : "null" ) + "}" ) ;
				contactCache.add(phoneId) ;
				
				if ( batchSize > 0 && newEntries.size() >= batchSize )
				{
					sendJSON(newEntries) ;
					newEntries.clear() ;
				}
				
			}
		}
		contactCursor.close();
		
		// parse phones
		contactCursor = this.contentResolver.query(
				Email.CONTENT_URI,
				new String[] { Email.ADDRESS, Email.DISPLAY_NAME_PRIMARY  },
				null, null, null
			);
		
		// iterate over the cursor
		while( contactCursor.moveToNext() )
		{
			stopIfInterrupted() ;
			String email = contactCursor.getString(0) ;
			String compositeName = contactCursor.getString(1);
			String emailId = "email_" + email ;
			if( !contactCache.contains( emailId ) )
			{
				newEntries.add( "\""+emailId+"\":{\"firstName\":" + ((compositeName != null && compositeName != email) ? ("\""+compositeName+"\"") : "null" ) + "}" ) ;
				contactCache.add(emailId) ;
				
				if ( batchSize > 0 && newEntries.size() >= batchSize )
				{
					sendJSON(newEntries) ;
					newEntries.clear() ;
				}
				
			}
		}
		contactCursor.close();
		
		callback.updateCache( contactCache ) ;
		
		sendJSON(newEntries) ;
		
		} catch(InterruptedException e) {// do nothing, we just wanted the function to end
		}
		
	}
	
	private void sendJSON( List<String> newEntries )
	{
		
		if( newEntries.size() > 0 )
		{
			String JSONret = "{" ;
			for( String entry : newEntries )
			{
				JSONret = JSONret + entry + ',' ;
			}
			JSONret = JSONret.substring(0, JSONret.length()-1) + '}' ;
			callback.dispatchEvent(AddressBookEvent.CONTACTS_UPDATED, JSONret) ;
		}
		
	}
	
	private void stopIfInterrupted() throws InterruptedException {
		Thread.yield() ;
		if(Thread.currentThread().isInterrupted()) {
			throw new InterruptedException("stopped by parent Thread") ;
		}
	}

}