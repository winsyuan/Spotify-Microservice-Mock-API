package com.csc301.songmicroservice;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class SongController {

	@Autowired
	private final SongDal songDal;

	private OkHttpClient client = new OkHttpClient();

	
	public SongController(SongDal songDal) {
		this.songDal = songDal;
	}

	
	@RequestMapping(value = "/getSongById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.findSongById(songId);

		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	@RequestMapping(value = "/getSongTitleById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongTitleById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));
		
		DbQueryStatus dbQueryStatus = songDal.getSongTitleById(songId); 
		
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		return response; 

	}

	
	@RequestMapping(value = "/deleteSongById/{songId}", method = RequestMethod.DELETE)
	public @ResponseBody Map<String, Object> deleteSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("DELETE %s", Utils.getUrl(request)));
		
		Request req = new Request.Builder().url("http://localhost:3002/deleteSongFromDb/"+songId).delete().build(); 
		DbQueryStatus dbQueryStatus; 
		
		try {
		  Response res = client.newCall(req).execute(); 
		  if(!res.isSuccessful()) {
		    throw new Exception(); 
		  }
		  JSONObject ret = new JSONObject(res.body().string()); 
		  if(!ret.getString("status").equals("OK")) {
		    throw new Exception(); 
		  }
		  dbQueryStatus = songDal.deleteSongById(songId); 
		  
		} catch(Exception e) {
		  dbQueryStatus = new DbQueryStatus("Error in deleting from favourite lists", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		return response;
	}

	
	@RequestMapping(value = "/addSong", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> addSong(@RequestParam Map<String, String> params,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));
		
		String name = params.get(Song.KEY_SONG_NAME);
		String artist = params.get(Song.KEY_SONG_ARTIST_FULL_NAME); 
		String album = params.get(Song.KEY_SONG_ALBUM);
		DbQueryStatus dbQueryStatus; 
		
		if(name == null || artist == null || album == null) {
		  dbQueryStatus = new DbQueryStatus("Missing parameters for song", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
		} else {
		  Song songToAdd = new Song(name, artist, album); 
		  dbQueryStatus = songDal.addSong(songToAdd); 
		}
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	@RequestMapping(value = "/updateSongFavouritesCount/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> updateFavouritesCount(@PathVariable("songId") String songId,
			@RequestParam("shouldDecrement") String shouldDecrement, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("data", String.format("PUT %s", Utils.getUrl(request)));
		
		DbQueryStatus dbQueryStatus;
		if(shouldDecrement == null || (!Boolean.parseBoolean(shouldDecrement) && !shouldDecrement.contains("false"))) {
		  dbQueryStatus = new DbQueryStatus("Must be true or false", DbQueryExecResult.QUERY_ERROR_GENERIC); 
		} else {
		  dbQueryStatus = songDal.updateSongFavouritesCount(songId, Boolean.parseBoolean(shouldDecrement)); 
		}
        response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

        return response;
	}
}