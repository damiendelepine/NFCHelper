package fr.repele.helpers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;


import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcF;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class NFCHelper {

	public static NFCHelper nfchelper = null;
	public static final String MIME_TEXT_PLAIN = "text/plain";
	public static final String MIME_VCARD = "Text/vcard";
	public static byte[] template_read;
	public static boolean templateNFCReady = false;
	public static Tag tag;
	public static boolean ndefAvaliable = false;

	public NFCHelper(Activity activity, Context context, NfcAdapter mNfcAdapter) {
		//mNfcAdapter = NfcAdapter.getDefaultAdapter(context);
		// on v�rifie si le hardware NFC est pr�sent sur le device
		if (mNfcAdapter == null) {
			// si le device n'a pas de module NFC, on quitte
			Toast.makeText(context, "This device doesn't support NFC !", Toast.LENGTH_LONG).show();
			//finish();
			return;
		}
		// on v�rifi� si le NFC est activ�
		if (!mNfcAdapter.isEnabled()) {
			// si le NFC n'est pas activ� on indique qu'il faut le faire
			Toast.makeText(context, "NFC has to be enabled !", Toast.LENGTH_LONG).show();
			activity.startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
		} else {
			// tout est OK, on attend la suite
			//Toast.makeText(context, "NFC enabled and waiting for a tag to read !", Toast.LENGTH_LONG).show();
		}
	}


	static class NdefStringReaderTask extends AsyncTask<Tag, Void, String> {
		public AsyncResponse delegate=null;

		@Override
		protected String doInBackground(Tag... params) {
			Tag tag = params[0];
			Ndef ndef = Ndef.get(tag);
			if (ndef == null) {
				// NDEF is not supported by this Tag.
				return null;
			}
			NdefMessage ndefMessage = ndef.getCachedNdefMessage();
			NdefRecord[] records = ndefMessage.getRecords();
			for (NdefRecord ndefRecord : records) {
				if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
					try {
						return readText(ndefRecord);
					} catch (UnsupportedEncodingException e) {
						// Log.e(TAG, "Unsupported Encoding", e);
					}
				}
			}
			return null;
		}



		private String readText(NdefRecord record)
				throws UnsupportedEncodingException {
			byte[] payload = record.getPayload();
			// Get the Text Encoding
			String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8"
					: "UTF-16";
			// Get the Language Code
			int languageCodeLength = payload[0] & 0063;

			return new String(payload, languageCodeLength + 1, payload.length
					- languageCodeLength - 1, textEncoding);
		}

		@Override
		protected void onPostExecute(String result) {
			delegate.processFinish(result);
		}
	}

	public interface AsyncResponse {
		void processFinish(String output);
	}

	static class GetNdefMessage extends AsyncTask<Tag, Void, String>{

		private Intent intent;
		private boolean nfcTechnoOK = false;
		public AsyncResponse delegate=null;

		public GetNdefMessage(Intent intent){
			this.intent = intent;
		}

		@Override
		protected void onPreExecute() {
			// r�cup�ration du type d'action demand� par l'intent
			String action = intent.getAction();

			// si un tag NDEF est d�tect�
			if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
				String type = intent.getType();									// on r�cup�re le type de la data

				// si c'est du texte
				if (MIME_TEXT_PLAIN.equals(type)) {
					Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);  // r�cup�ration du tag
					try {
						new NdefTemplateReaderTask().execute(tag).get();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	// lancement de la task de lecture
				} 
				//sinon
				else {
					Log.d("Lecture du tag", "Wrong mime type: " + type);		// on indique que ce n'est pas du texte
					//cancel(true);
				}


			}

			// sinon on lance un intent vers une autre appli
			else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
				Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);		// on r�cup�re l'objet tag
				String[] techList = tag.getTechList();							// on r�cup�re la liste des techonologies disponibles sur le tag
				String searchedTech = NfcF.class.getName();						// on r�cup�re le nom de la technologie Ndef

				// on recherche la technologie Ndef compatible avec le tag
				for (String tech : techList) {

					// si on trouve une technologie compatible
					if (searchedTech.equals(tech)) {
						nfcTechnoOK = true;
					}
				}
			}		
			if(nfcTechnoOK){
				super.onPreExecute();
			}
			else{
				//cancel(true);
			}
		}


		@Override
		protected String doInBackground(Tag... params) {

			Tag tag = params[0];
			NfcF nfcF = NfcF.get(tag);
			Ndef ndef = Ndef.get(nfcF.getTag());
			if (ndef == null) {
				// NDEF is not supported by this Tag.
				return null;
			}

			NdefMessage ndefMessage = ndef.getCachedNdefMessage();
			NdefRecord[] records = ndefMessage.getRecords();
			for (NdefRecord ndefRecord : records) {
				System.out.println("Print du ndef record : " + ndefRecord.toString());
				if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
					try {
						return ndefRecord.toString();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

			return null;
		}


		@Override
		protected void onPostExecute(String result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
		}

		
		



	}

	static class NdefTemplateReaderTask extends AsyncTask<Tag, Void, byte[]> {

		@Override
		protected byte[] doInBackground(Tag... params) {
			Tag tag = params[0];
			Ndef ndef = Ndef.get(tag);
			if (ndef == null) {
				// NDEF is not supported by this Tag.
				return null;
			}
			NdefMessage ndefMessage = ndef.getCachedNdefMessage();
			NdefRecord[] records = ndefMessage.getRecords();
			for (NdefRecord ndefRecord : records) {
				if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
					try {
						return readByteArray(ndefRecord);
					} catch (UnsupportedEncodingException e) {
						// Log.e(TAG, "Unsupported Encoding", e);
					}
				}
			}
			return null;
		}



		private byte[] readByteArray(NdefRecord record)	throws UnsupportedEncodingException {

			byte[] payload = record.getPayload();
			String lang = "en";
			byte[] langBytes = lang.getBytes("US-ASCII");
			int langLength = langBytes.length;
			template_read = new byte[payload.length];	 
			template_read = payload;
			return template_read;
		}

		@Override
		protected void onPostExecute(byte[] template) {
			if (template != null) {
				templateNFCReady = true;
			}
		}
	}

	public static boolean read(Intent intent)
	{		
		// r�cup�ration du type d'action demand� par l'intent
		String action = intent.getAction();

		// si un tag NDEF est d�tect�
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
			String type = intent.getType();									// on r�cup�re le type de la data

			// si c'est du texte
			if (MIME_TEXT_PLAIN.equals(type)) {
				Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);  // r�cup�ration du tag
				try {
					new NdefTemplateReaderTask().execute(tag).get();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}	// lancement de la task de lecture
			} 
			//sinon
			else {
				Log.d("Lecture du tag", "Wrong mime type: " + type);		// on indique que ce n'est pas du texte
			}


		}

		// sinon on lance un intent vers une autre appli
		else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);		// on r�cup�re l'objet tag
			String[] techList = tag.getTechList();							// on r�cup�re la liste des techonologies disponibles sur le tag
			String searchedTech = Ndef.class.getName();						// on r�cup�re le nom de la technologie Ndef

			// on recherche la technologie Ndef compatible avec le tag
			for (String tech : techList) {

				// si on trouve une technologie compatible
				if (searchedTech.equals(tech)) {
					// on lance la task de lecture
					new NdefStringReaderTask().execute(tag);
					break;
				}
			}
		}
		return true;
	}

	private static NdefRecord createStringRecord(String text) throws UnsupportedEncodingException {

		//create the message in according with the standard
		String lang = "en";
		byte[] textBytes = text.getBytes();
		byte[] langBytes = lang.getBytes("US-ASCII");
		int langLength = langBytes.length;
		int textLength = textBytes.length;

		byte[] payload = new byte[1 + langLength + textLength];
		payload[0] = (byte) langLength;

		// copy langbytes and textbytes into payload
		System.arraycopy(langBytes, 0, payload, 1, langLength);
		System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

		NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);
		return recordNFC;
	}

	static void writeString(String text, Tag tag) throws IOException, FormatException {

		NdefRecord[] records = { createStringRecord(text) };
		NdefMessage message = new NdefMessage(records); 
		Ndef ndef = Ndef.get(tag);
		ndef.connect();
		ndef.writeNdefMessage(message);
		ndef.close();
	}

	private static NdefRecord createTemplateRecord(byte[] template) throws UnsupportedEncodingException {

		NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], template);
		return recordNFC;
	}

	public static void writeTemplate(byte[] template, Tag tag) throws IOException, FormatException {

		NdefRecord[] records = { createTemplateRecord(template) };
		NdefMessage message = new NdefMessage(records); 
		Ndef ndef = Ndef.get(tag);
		ndef.connect();
		ndef.writeNdefMessage(message);
		ndef.close();
	}

	public static String byteArrayToString(byte[] bytes)
	{
		return new String(bytes);
	}

	public static Tag getTag() {
		return tag;
	}


	public static void setTag(Tag tag) {
		NFCHelper.tag = tag;
		ndefAvaliable = false;
		for(int i = 0; i < tag.getTechList().length; i ++){
			if(tag.getTechList()[i].equals("android.nfc.tech.Ndef")){
				ndefAvaliable = true;
				return;
			}
		}
	}

	public void setupForegroundDispatch(final Activity activity, NfcAdapter adapter) {
		final Intent intent = new Intent(activity.getApplicationContext(), activity.getClass());
		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		final PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 0, intent, 0);
		IntentFilter[] filters = new IntentFilter[1];
		String[][] techList = new String[][] { new String[] { IsoDep.class.getName() }, 
				new String[] { MifareClassic.class.getName() },
				new String[] { Ndef.class.getName() },
				new String[] { NfcA.class.getName() },
				new String[] { NfcF.class.getName() }
		};		// Notice that this is the same filter as in our manifest.
		filters[0] = new IntentFilter();
		filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
		filters[0].addCategory(Intent.CATEGORY_DEFAULT);
		try {
			filters[0].addDataType(MIME_TEXT_PLAIN);
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("Check your mime type.");
		}
		adapter.enableForegroundDispatch(activity, pendingIntent, filters, techList);
	}

	public void stopForegroundDispatch(final Activity activity, NfcAdapter adapter) {
		adapter.disableForegroundDispatch(activity);
	}



}
