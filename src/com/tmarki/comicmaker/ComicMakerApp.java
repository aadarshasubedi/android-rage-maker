package com.tmarki.comicmaker;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;
import android.os.FileObserver;

import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.Vector;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface.OnClickListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import com.tmarki.comicmaker.ColorPickerDialog;
import com.tmarki.comicmaker.ComicEditor;
import com.tmarki.comicmaker.R;
import com.tmarki.comicmaker.ComicEditor.ComicState;
import com.tmarki.comicmaker.Picker;
import com.tmarki.comicmaker.ColorPickerDialog.OnColorChangedListener;
import com.tmarki.comicmaker.ComicEditor.TouchModes;
import com.tmarki.comicmaker.Picker.OnWidthChangedListener;
import com.tmarki.comicmaker.TextObject.FontType;
import com.tmarki.comicmaker.ComicSettings;




public class ComicMakerApp extends Activity implements ColorPickerDialog.OnColorChangedListener, OnWidthChangedListener {
	private ComicEditor mainView;
	private Map<CharSequence, Map<CharSequence, Vector<CharSequence>>> externalImages = new HashMap<CharSequence, Map<CharSequence, Vector<CharSequence>>>();
	private CharSequence packSelected;
	private CharSequence folderSelected;
	private ImageSelect imgsel = null;
	private FontSelect fontselect = null;
	private ComicSettings settings = null;
	private MenuItem menuitem_OtherSource = null;
	private Map<MenuItem, CharSequence> menuitems_Packs = new HashMap<MenuItem, CharSequence> ();
	
	void readExternalFiles(){
		externalImages = PackHandler.getBundles(getAssets ());
	}
	
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d ("RAGE", "Save instance");
		super.onSaveInstanceState(outState);
		outState.putSerializable("touchMode", mainView.getmTouchMode());
		outState.putInt("currentColor", mainView.getCurrentColor());
		outState.putInt("currentStrokeWidth", mainView.getCurrentStrokeWidth());
		outState.putInt("currentPanelCount", mainView.getPanelCount());
		outState.putBoolean("drawGrid", mainView.isDrawGrid());
		outState.putFloat("canvasScale", mainView.getCanvasScale());
		outState.putInt("canvasX", mainView.getmCanvasOffset().x);
		outState.putInt("canvasY", mainView.getmCanvasOffset().y);
		saveExternalSources(outState);
		saveImagesToBundle(outState, mainView.getImageObjects(), "");
		saveTextObjectsToBundle (outState, mainView.getTextObjects(), "");
		saveLinesToBundle (outState, mainView.getPoints(), mainView.getPaints(), "");
		Vector<ComicEditor.ComicState> history = mainView.getHistory();
		outState.putInt("historySize", history.size ());
		for (int i = 0; i < history.size (); ++i) {
			saveImagesToBundle(outState, history.get (i).mDrawables, String.format("h%s", i));
			saveTextObjectsToBundle (outState, history.get (i).mTextDrawables, String.format("h%s", i));
			saveLinesToBundle (outState, history.get (i).mLinePoints, history.get (i).mLinePaints, String.format("h%s", i));
			outState.putInt(String.format ("h%spanelCount", i), history.get (i).mPanelCount);
		}
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainView = new ComicEditor (this);
        registerForContextMenu(mainView);
        setContentView(mainView);
        if (savedInstanceState != null) {
        	if (savedInstanceState.getSerializable("touchMode") != null)
        		mainView.setmTouchMode((ComicEditor.TouchModes)savedInstanceState.getSerializable("touchMode"));
        	mainView.setCurrentColor(savedInstanceState.getInt("currentColor"));
            mainView.setCurrentStrokeWidth(savedInstanceState.getInt("currentStrokeWidth"));
            mainView.setPanelCount(savedInstanceState.getInt("currentPanelCount"));
            mainView.setDrawGrid(savedInstanceState.getBoolean("drawGrid"));
            mainView.setCanvasScale (savedInstanceState.getFloat("canvasScale"));
            mainView.setmCanvasOffset(new Point (savedInstanceState.getInt ("canvasX"), savedInstanceState.getInt ("canvasY")));
            PackHandler.setAssetManager(getAssets ());
            loadExternalSources(savedInstanceState);
        }
        else
        	readExternalFiles();
        for (ImageObject io : loadImagesFromBundle (savedInstanceState, "")) {
        	mainView.pureAddImageObject(io);
        }
        for (TextObject to : loadTextsFromBundle (savedInstanceState, "")) {
        	mainView.pureAddTextObject(to);
        }
        LinkedList<LinkedList<Point>> points = loadPointsFromBundle (savedInstanceState, "");
        for (int i = 0; i < points.size (); ++i) {
        	LinkedList<Point> p = points.get(i);
        	if (p == null)
        		continue;
        	mainView.pureAddLine (p, getPaintForPoint(savedInstanceState, i, ""));
        }
        mainView.resetHistory();
        int hs = 0;
        if (savedInstanceState != null)
        	hs = savedInstanceState.getInt("historySize", 0);
        for (int i = 0; i < hs; ++i) {
        	ComicState cs = mainView.getStateCopy();
        	cs.mDrawables = loadImagesFromBundle(savedInstanceState, String.format("h%s", i));
        	cs.mTextDrawables = loadTextsFromBundle(savedInstanceState, String.format("h%s", i));
        	cs.mLinePoints = loadPointsFromBundle(savedInstanceState, String.format("h%s", i));
        	cs.mLinePaints = new LinkedList<Paint> ();
        	cs.mPanelCount = savedInstanceState.getInt(String.format("h%spanelCount", i));
        	for (int j = 0; j < cs.mLinePoints.size (); ++j) {
        		cs.mLinePaints.add(getPaintForPoint(savedInstanceState, j, String.format("h%s", i)));
        	}
        	mainView.pushHistory(cs);
        }
        mainView.invalidate();
    }
    
    private void saveExternalSources (Bundle outState) {
    	int i = 0;
    	outState.putInt("packCount", externalImages.keySet().size ());
    	for (CharSequence pack : externalImages.keySet()) {
    		outState.putCharSequence(String.format ("pack%s", i), pack);
        	outState.putInt(String.format ("folderCount%s", i), externalImages.get (pack).keySet().size ());
        	int j = 0;
        	for (CharSequence folder : externalImages.get(pack).keySet()) {
        		outState.putCharSequence(String.format ("folder%s-%s", i, j), folder);
            	outState.putInt(String.format ("fileCount%s-%s", i, j), externalImages.get (pack).get (folder).size ());
            	int k = 0;
            	for (CharSequence file : externalImages.get (pack).get (folder)) {
            		outState.putCharSequence(String.format ("file%s-%s-%s", i, j, k++), file);
            	}
            	j++;
        	}
        	i++;
    	}
    }
    
    private void saveImagesToBundle (Bundle outState, Vector<ImageObject> ios, String tag) {
		outState.putInt(tag + "imageObjectCount", ios.size ());
        for (int i = 0; i < ios.size (); ++i) {
        	int[] params = new int[2];
        	params[0] = ios.get(i).getPosition().x;
        	params[1] = ios.get(i).getPosition().y;
        	outState.putIntArray(String.format(tag + "ImageObject%dpos", i), params);
        	outState.putFloat(String.format(tag + "ImageObject%drot", i), ios.get(i).getRotation());
        	outState.putFloat(String.format(tag + "ImageObject%dscale", i), ios.get (i).getScale ());
        	outState.putInt(String.format(tag + "ImageObject%drid", i), ios.get (i).getDrawableId());
        	outState.putString(String.format(tag + "ImageObject%dpack", i), ios.get (i).pack);
        	outState.putString(String.format(tag + "ImageObject%dfolder", i), ios.get (i).folder);
        	outState.putString(String.format(tag + "ImageObject%dfile", i), ios.get (i).filename);
        	outState.putBoolean(String.format(tag + "ImageObject%dfv", i), ios.get(i).isFlipVertical());
        	outState.putBoolean(String.format(tag + "ImageObject%dfh", i), ios.get(i).isFlipHorizontal());
        }
    }
    
    private void saveTextObjectsToBundle (Bundle outState, Vector<TextObject> tobs, String tag) {
		outState.putInt(tag + "textObjectCount", tobs.size ());
        for (int i = 0; i < tobs.size (); ++i) {
        	outState.putInt(String.format(tag + "TextObject%dx", i), tobs.get (i).getX());
        	outState.putInt(String.format(tag + "TextObject%dy", i), tobs.get (i).getY());
        	outState.putInt(String.format(tag + "TextObject%dsize", i), tobs.get (i).getTextSize());
        	outState.putInt(String.format(tag + "TextObject%dcolor", i), tobs.get (i).getColor());
        	outState.putSerializable(String.format(tag + "TextObject%dtypeface", i), tobs.get (i).getTypeface());
        	outState.putString(String.format(tag + "TextObject%dtext", i), tobs.get (i).getText());
        	outState.putBoolean(String.format(tag + "TextObject%dbold", i), tobs.get (i).isBold());
        	outState.putBoolean(String.format(tag + "TextObject%ditalic", i), tobs.get (i).isItalic());
        }
    }
    
    private void saveLinesToBundle (Bundle outState, LinkedList<LinkedList<Point>> points, LinkedList<Paint> paints, String tag) {
		outState.putInt(tag + "lineCount", points.size ());
        for (int i = 0; i < points.size (); ++i) {
        	outState.putInt(String.format(tag + "line%dpointcount", i), points.get (i).size());
            for (int j = 0; j < points.get (i).size (); ++j) {
            	outState.putInt(String.format(tag + "line%dpoint%dx", i, j), points.get (i).get(j).x);
            	outState.putInt(String.format(tag + "line%dpoint%dy", i, j), points.get (i).get(j).y);
            }
            outState.putFloat(String.format(tag + "line%dstroke", i), paints.get (i).getStrokeWidth());
            outState.putInt(String.format(tag + "line%dcolor", i), paints.get (i).getColor());
        }
    }

    private void loadExternalSources (Bundle savedInstanceState) {
    	externalImages.clear();
    	int pc = savedInstanceState.getInt("packCount");
    	for (int i = 0; i < pc; ++i) {
    		CharSequence pack = savedInstanceState.getCharSequence(String.format ("pack%s", i));
    		Map<CharSequence, Vector<CharSequence>> folders = new HashMap<CharSequence, Vector<CharSequence>> ();
    		int foc = savedInstanceState.getInt(String.format ("folderCount%s", i));
    		for (int j = 0; j < foc; ++j) {
    			Vector<CharSequence> files = new Vector<CharSequence> ();
    			CharSequence folder = savedInstanceState.getCharSequence(String.format ("folder%s-%s", i, j));
    			int fic = savedInstanceState.getInt(String.format ("fileCount%s-%s", i, j));
    			for (int k = 0; k < fic; ++k) {
    				files.add(savedInstanceState.getCharSequence(String.format ("file%s-%s-%s", i, j, k)));
    			}
    			folders.put(folder, files);
    		}
    		externalImages.put(pack, folders);
    	}
/*    	for (CharSequence pack : externalImages.keySet()) {
    		outState.putCharSequence(String.format ("pack%s", i++), pack);
        	outState.putInt(String.format ("folderCount%s", i), externalImages.get (pack).keySet().size ());
        	int j = 0;
        	for (CharSequence folder : externalImages.get(pack).keySet()) {
        		outState.putCharSequence(String.format ("folder%s-%s", i, j++), folder);
            	outState.putInt(String.format ("fileCount%s-%s", i, j), externalImages.get (pack).get (folder).size ());
            	int k = 0;
            	for (CharSequence file : externalImages.get (pack).get (folder)) {
            		outState.putCharSequence(String.format ("file%s-%s-%s", i, j, k++), file);
            	}
        	}
    	}*/
    }
    
    private Vector<ImageObject> loadImagesFromBundle (Bundle savedInstanceState, String tag) {
    	Vector<ImageObject> ret = new Vector<ImageObject> ();
        int ioCount = 0;
        if (savedInstanceState != null)
        	ioCount = savedInstanceState.getInt(tag + "imageObjectCount", 0);
        for (int i = 0; i < ioCount; ++i) {
        	int[] params = savedInstanceState.getIntArray(String.format(tag + "ImageObject%dpos", i));
        	float rot = savedInstanceState.getFloat(String.format(tag + "ImageObject%drot", i));
        	float sc = savedInstanceState.getFloat(String.format(tag + "ImageObject%dscale", i));
        	int rid = savedInstanceState.getInt(String.format(tag + "ImageObject%drid", i));
        	String pack = savedInstanceState.getString(String.format(tag + "ImageObject%dpack", i));
        	String folder = savedInstanceState.getString(String.format(tag + "ImageObject%dfolder", i));
        	String file = savedInstanceState.getString(String.format(tag + "ImageObject%dfile", i));
        	ImageObject io = null;
        	if (rid > 0) {
        		Drawable dr = getResources().getDrawable(rid);
        		io = new ImageObject(dr, params[0], params[1], rot, sc, rid, pack, folder, file);
        		//mainView.addImageObject (dr, params[0], params[1], rot, sc, rid);
        	}
        	else if (pack.length() > 0) { 
        		io = new ImageObject(PackHandler.getPackDrawable(pack, folder, file), params[0], params[1], rot, sc, rid, pack, folder, file);
//    			mainView.addImageObject(PackHandler.getPackDrawable(pack, folder, file), params[0], params[1], rot,sc, rid, pack, folder, file);
        	}
        	else if (file.length() > 0) {
				BitmapDrawable bdr = new BitmapDrawable(file);
        		io = new ImageObject(bdr, params[0], params[1], rot, sc, rid, pack, folder, file);
//				mainView.addImageObject(bdr, params[0], params[1], rot, sc, rid, pack, folder, file);
        	}
        	if (io != null) {
        		io.setFlipHorizontal(savedInstanceState.getBoolean(String.format(tag + "ImageObject%dfh", i)));
        		io.setFlipVertical(savedInstanceState.getBoolean(String.format(tag + "ImageObject%dfv", i)));
        		ret.add (io);
        	}
        }
        return ret;

    }

    private Vector<TextObject> loadTextsFromBundle (Bundle savedInstanceState, String tag) {
    	Vector<TextObject> ret = new Vector<TextObject> ();
        int txCount = 0;
        if (savedInstanceState != null)
        	txCount = savedInstanceState.getInt(tag + "textObjectCount", 0);
        for (int i = 0; i < txCount; ++i) {
        	int x = savedInstanceState.getInt(String.format(tag + "TextObject%dx", i));
        	int y = savedInstanceState.getInt(String.format(tag + "TextObject%dy", i));
        	int s = savedInstanceState.getInt(String.format(tag + "TextObject%dsize", i));
        	int c = savedInstanceState.getInt(String.format(tag + "TextObject%dcolor", i));
        	TextObject.FontType ft = (FontType) savedInstanceState.getSerializable(String.format(tag + "TextObject%dtypeface", i));
        	String text = savedInstanceState.getString(String.format(tag + "TextObject%dtext", i));
        	Boolean bold = savedInstanceState.getBoolean(String.format(tag + "TextObject%dbold", i));
        	Boolean italic = savedInstanceState.getBoolean(String.format(tag + "TextObject%ditalic", i));
        	ret.add (new TextObject (x, y, s, c, ft, text, bold, italic));
        }
    	return ret;
    }

    private LinkedList<LinkedList<Point>> loadPointsFromBundle (Bundle savedInstanceState, String tag) {
    	LinkedList<LinkedList<Point>> ret = new LinkedList<LinkedList<Point>> ();
        int pCount = 0;
        if (savedInstanceState != null) 
        	pCount = savedInstanceState.getInt(tag + "lineCount", 0);
        for (int i = 0; i < pCount; ++i) {
        	LinkedList<Point> p = new LinkedList<Point>();
        	int cnt = savedInstanceState.getInt(String.format(tag + "line%dpointcount", i));
        	for (int j = 0; j < cnt; ++j) {
        		int x = savedInstanceState.getInt(String.format(tag + "line%dpoint%dx", i, j));
        		int y = savedInstanceState.getInt(String.format(tag + "line%dpoint%dy", i, j));
        		p.add(new Point (x, y));
        	}
/*        	Paint pp = new Paint ();
        	pp.setStrokeWidth(savedInstanceState.getFloat(String.format(tag + "line%dstroke", i)));
        	pp.setColor(savedInstanceState.getInt(String.format(tag + "line%dcolor", i)));*/
        	ret.add (p);
//        	mainView.addLine(p, pp); 
        }
    	return ret;
    }
    
    private Paint getPaintForPoint (Bundle savedInstanceState, int lineInd, String tag) {
    	Paint pp = new Paint ();
    	pp.setStrokeWidth(savedInstanceState.getFloat(String.format(tag + "line%dstroke", lineInd)));
    	pp.setColor(savedInstanceState.getInt(String.format(tag + "line%dcolor", lineInd)));
    	return pp;
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
		SubMenu sm = menu.addSubMenu("Add Image");
	    inflater.inflate(R.menu.main_menu, menu);
		menuitems_Packs.clear();
        for (CharSequence s : externalImages.keySet()) {
        	if (sm != null) {
        		MenuItem mi = sm.add("Pack: " + s);
        		menuitems_Packs.put(mi, s);
        	}
	    }
	    menuitem_OtherSource = sm.add("From other source");
	    Log.d ("RAGE", "Main menu");
	    return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		ImageObject io = mainView.getSelected();
		if (menu.size() == 0 && io != null) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.edit_menu, menu);
			menu.findItem(R.id.tofront).setVisible(io.isInBack());
			menu.findItem(R.id.toback).setVisible(!io.isInBack());
			mainView.resetClick();
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		ImageObject io = mainView.getSelected();
		if (item.getItemId() == R.id.mode_hand)
			mainView.setmTouchMode(TouchModes.HAND);
		else if (item.getItemId() == R.id.mode_pencil)
			mainView.setmTouchMode(TouchModes.PENCIL);
		else if (item.getItemId() == R.id.mode_text)
			mainView.setmTouchMode(TouchModes.TEXT);
		else if (item.getItemId() == R.id.mode_line)
			mainView.setmTouchMode(TouchModes.LINE);
		else if (item.getItemId() == R.id.toback && io != null)
			io.setInBack(true);
		else if (item.getItemId() == R.id.tofront && io != null)
			io.setInBack(false);
		else if (item.getItemId() == R.id.remove && io != null) {
			mainView.removeImageObject(io);
		}
		else if (item.getItemId() == R.id.flipH && io != null) {
			io.setFlipHorizontal(!io.isFlipHorizontal());
		}
		else if (item.getItemId() == R.id.flipV && io != null) {
			io.setFlipVertical(!io.isFlipVertical());
		}
		mainView.invalidate();
		Log.d ("RAGE", item.toString());
		return super.onContextItemSelected(item);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d ("RAGE", item.toString());
		switch (item.getItemId())
		{
		case R.id.about:
			AlertDialog alertDialog;
			alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle("About Rage Comic Maker");
			alertDialog.setMessage("Rage Comic Maker v1.0\nfor Android\n\n(c) 2011 Tamas Marki\nThis is free software. Use it at your own risk.");
			alertDialog.setButton("Close", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					
				}
			});
			alertDialog.show();
			break;
		case (R.id.pen_color):
		case (R.id.text_color):
//			ColorPickerDialog cpd = new ColorPickerDialog(mainView.getContext(), this, mainView.getCurrentColor());
			ColorPickerDialog cpd = new ColorPickerDialog(mainView.getContext(), this, "key", mainView.getCurrentColor(), mainView.getCurrentColor());
//			ColorPickerDialog(Context context, OnColorChangedListener listener, String key, int initialColor, int defaultColor) {
			cpd.show();
			break;
		case (R.id.pen_width):
			Picker np = new Picker (mainView.getContext(), this, mainView.getCurrentStrokeWidth());
			np.show();
			break;
		case (R.id.clear):
			AlertDialog alertDialog2;
			alertDialog2 = new AlertDialog.Builder(this).create();
			alertDialog2.setTitle("Confirmation");
			alertDialog2.setMessage("Clear comic?");
			alertDialog2.setButton("Yes", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					mainView.resetObjects();
					mainView.invalidate();
				}
			});
			alertDialog2.setButton2 ("No", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					
				}
			});
			alertDialog2.show();
			break;
		
/*		case (R.id.add_pack): //  comic pack
			if (externalImages.size() == 0)
				readExternalFiles();
			if (externalImages.size() > 0) {
				doComicPackSelect();
			}
			else {
				AlertDialog alertDialog3;
				alertDialog3 = new AlertDialog.Builder(this).create();
				alertDialog3.setTitle("Comic Packs");
				alertDialog3.setMessage("No comic packs were found. Make sure you place them in the 'ComicMaker' directory on your external storage (SD card).");
				alertDialog3.setButton("Ok", new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						
					}
				});
				alertDialog3.show();
			}
			break;*/
		case (R.id.text_type):
			fontselect = new FontSelect (this, setFontTypeListener, mainView.getDefaultFontSize(), mainView.isDefaultBold(), mainView.isDefaultItalic());
			fontselect.show();
			break;
		case (R.id.settings):
			settings = new ComicSettings (this, mainView.getPanelCount(), mainView.isDrawGrid(), new View.OnClickListener() {

				public void onClick(View v) {
					mainView.setPanelCount(settings.getPanelCount ());
					mainView.setDrawGrid(settings.getDrawGrid());
					settings.dismiss();
					mainView.invalidate();
				}
			});
			settings.show();
			break;
		case (R.id.save):
			AlertDialog.Builder alert = new AlertDialog.Builder(this);
	
			alert.setTitle("Enter file name");
	//		alert.setMessage("Message");
	
			// Set an EditText view to get user input 
			final EditText input = new EditText(this);
			alert.setView(input);
	
			alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String value = input.getText().toString();
					Bitmap b = mainView.getSaveBitmap();
					CharSequence text = "Comic saved successfully!";
					try {
						java.io.File f = new java.io.File (Environment.getExternalStorageDirectory() + "/Pictures");
						if (!f.exists())
							f.mkdirs();
						b.compress(CompressFormat.JPEG, 95, new FileOutputStream(Environment.getExternalStorageDirectory() + "/Pictures/" + value + ".jpg"));
					} catch (FileNotFoundException e) {
						e.printStackTrace();
						text = "There was an error while saving the comic.";
					}
					int duration = Toast.LENGTH_SHORT;
					Toast toast = Toast.makeText(getApplicationContext(), text, duration);
					toast.show();
			  }
			});
	
			alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			  public void onClick(DialogInterface dialog, int whichButton) {
			  }
			});
	
			alert.show();
			break;
		default:
			if (menuitem_OtherSource == item) {
				
			// To open up a gallery browser
				Intent intent = new Intent();
				intent.setType("image/*");
				intent.setAction(Intent.ACTION_GET_CONTENT);
				startActivityForResult(Intent.createChooser(intent, "Select Picture"),1);
				Log.d ("RAGE", "item ID: " + String.valueOf(item.getItemId()));
			}
			else if (menuitems_Packs.containsKey(item)) {
//				CharSequence[] cs = (CharSequence[]) externalImages.keySet().toArray(new CharSequence[externalImages.keySet().size()]);
				packSelected = menuitems_Packs.get(item);
				doComicPackFolderSelect();
			}
			break;
		}
			
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) { 

		if (resultCode == RESULT_OK) {

			if (requestCode == 1) {
				String fname = getRealPathFromURI (data.getData());
				BitmapDrawable bdr = new BitmapDrawable(fname);
				mainView.addImageObject(bdr, mainView.getmCanvasOffset().x + 10, mainView.getmCanvasOffset().y + 10, 0.0f, 1.0f, 0, "", "", fname);
			}
		}
	}

	// And to convert the image URI to the direct file system path of the image file
	public String getRealPathFromURI(Uri contentUri) {

		// can post image
		String [] proj={MediaStore.Images.Media.DATA};
		Cursor cursor = managedQuery( contentUri,
				proj, // Which columns to return
				null,       // WHERE clause; which rows to return (all rows)
				null,       // WHERE clause selection arguments (none)
				null); // Order-by clause (ascending by name)
		int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		cursor.moveToFirst();

		return cursor.getString(column_index);
	}

	private void doComicPackSelect () {
		CharSequence[] cs = (CharSequence[]) externalImages.keySet().toArray(new CharSequence[externalImages.keySet().size()]);
		Arrays.sort(cs);
		AlertDialog alertDialog3;
		alertDialog3 = new AlertDialog.Builder(this)
        .setTitle("Select Pack")
        .setItems(cs, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
				CharSequence[] cs = (CharSequence[]) externalImages.keySet().toArray(new CharSequence[externalImages.keySet().size()]);
				Arrays.sort(cs);
				packSelected = cs[which];
				doComicPackFolderSelect();
            }
        })
        .create();
		alertDialog3.show();
	}
	
	private void doComicPackFolderSelect () {
		CharSequence[] ccs = (CharSequence[]) externalImages.get (packSelected).keySet().toArray(new CharSequence[externalImages.get (packSelected).keySet().size()]);
		Arrays.sort(ccs);
		AlertDialog alertDialog;
		alertDialog = new AlertDialog.Builder(mainView.getContext())
        .setTitle("Select Folder")
        .setItems(ccs, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which2) {

                /* User clicked so do some stuff */
				CharSequence[] ccs = (CharSequence[]) externalImages.get (packSelected).keySet().toArray(new CharSequence[externalImages.get (packSelected).keySet().size()]);
				Arrays.sort(ccs);
				folderSelected = ccs[which2];
				doComicPackImageSelect();
/*            	new AlertDialog.Builder(mainView.getContext())
                        .setMessage("You selected: " + which2 + " , " + ccs[which2])
                        .show();*/
            }
        })
        .create();
		alertDialog.show();
	}

	private OnItemClickListener setFontTypeListener = new OnItemClickListener(){
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			Log.d ("RAGE", "Clicked: " + String.valueOf(arg2));
			fontselect.dismiss();
			mainView.setCurrentFont(TextObject.FontType.values()[arg2]);
			mainView.setDefaultBold(fontselect.isBold());
			mainView.setDefaultItalic(fontselect.isItalic());
//			mainView.setDefaultFontSize(fontselect.getFontSize());
			mainView.invalidate();
		}
    };

	private OnItemClickListener addFromPackListener = new OnItemClickListener(){
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			Log.d ("RAGE", "Clicked: " + String.valueOf(arg2));
			imgsel.dismiss();
			String fname = externalImages.get (packSelected).get(folderSelected).get (arg2).toString();
			mainView.addImageObject(PackHandler.getPackDrawable(packSelected.toString(), folderSelected.toString(), fname), 100, 100, 0.0f, 1.0f, 0, packSelected.toString(), folderSelected.toString(), fname);
		}
    };
	private void doComicPackImageSelect () {
		imgsel = new ImageSelect(this, externalImages, packSelected.toString(), folderSelected.toString(), addFromPackListener, mainView.getWidth() > mainView.getHeight () ? mainView.getWidth () : mainView.getHeight ());
		imgsel.show();
	}
	

	public void colorChanged(int c) {
		mainView.setCurrentColor(c);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK)
		{
			if (!mainView.popState()) {
				AlertDialog alertDialog;
				alertDialog = new AlertDialog.Builder(this).create();
				alertDialog.setTitle("Confirmation");
				alertDialog.setMessage("Are you sure you want to exit?");
				alertDialog.setButton("Yes", new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish ();
					}
				});
				alertDialog.setButton2("No", new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						
					}
				});
				alertDialog.show();
			}
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.pen_color).setVisible(mainView.getmTouchMode() == TouchModes.PENCIL || mainView.getmTouchMode() == TouchModes.LINE);
		menu.findItem(R.id.pen_width).setVisible(mainView.getmTouchMode() == TouchModes.PENCIL || mainView.getmTouchMode() == TouchModes.LINE);
		menu.findItem(R.id.text_color).setVisible(mainView.getmTouchMode() == TouchModes.TEXT);
		menu.findItem(R.id.text_type).setVisible(mainView.getmTouchMode() == TouchModes.TEXT);

	    Log.d ("RAGE", "Main menu");
		return super.onPrepareOptionsMenu(menu);
	}

	public void widthChanged(int width) {
		mainView.setCurrentStrokeWidth(width);
		
	}


	public void colorChanged(String key, int color) {
		
		mainView.setCurrentColor(color);
	}

}