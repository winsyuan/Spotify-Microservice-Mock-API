package com.csc301.profilemicroservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.util.Pair;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			}
			session.close();
		}
	}
	
	/**
	 * Creates a new profile with username, fullname and password 
	 */
	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		DbQueryStatus exit;
		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				String favList = userName + "-" + "favorites";
				String queryStr = "CREATE (p:profile {userName: $x, fullName: $y, password: $z}) CREATE (w:playlist {plName : $s}) MERGE (p)-[relation:created]->(w) RETURN p";
				StatementResult result = trans.run(queryStr, Values.parameters("x", userName, "y", fullName, "z", password, "s", favList));
				if(result.hasNext()) {
					exit = new DbQueryStatus("OK", DbQueryExecResult.QUERY_OK);
				} else {
					exit = new DbQueryStatus("Error in creating profile", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
				trans.success();
			} 
			session.close();
		}
		return exit;
	}

	/**
	 * Checks if both users exist in the neo4j db
	 * Checks if the user is already following the user
	 * Creates the relation from user to friend
	 */
	@Override
	public DbQueryStatus followFriend(String userName, String frndUserName) {
		DbQueryStatus exit;
		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
//				checks if the user is an actual node in db
				StatementResult first = trans.run("MATCH (a:profile {userName: $x}) RETURN a", Values.parameters("x", userName));
				if(!first.hasNext()) {
					exit = new DbQueryStatus("Username not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					return exit;
				}
//				checks if the friend user is an actual node in db
				StatementResult second = trans.run("MATCH (a:profile {userName: $y}) RETURN a", Values.parameters("y", frndUserName));
				if(!second.hasNext()) {
					exit = new DbQueryStatus("Friend username not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
					return exit;
				}
//				checks if there exist a exist a relation with the two users
				StatementResult third = trans.run("MATCH (a:profile {userName: $x}), (b:profile {userName: $y}) MATCH (a)-[r:follows]->(b) RETURN r", Values.parameters("x", userName, "y", frndUserName));
				if(third.hasNext()) {
					exit = new DbQueryStatus("Already following user", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return exit;
				}
				String queryStr = "MATCH (a:profile {userName: $x}), (b:profile {userName: $y}) MERGE (a)-[relation:follows]->(b)";
				trans.run(queryStr, Values.parameters("x", userName, "y", frndUserName));
				trans.success();
			}
			session.close();
		}
		exit = new DbQueryStatus("OK", DbQueryExecResult.QUERY_OK);
		return exit;
	}

	/**
	 * Checks if user and friend user are profile nodes in neo4j
	 * Checks if the user is following the friend user
	 * Deletes the relation if the friend is following the user
	 */
	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		DbQueryStatus exit;

		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
//				checks if the user is an actual node in db
				StatementResult first = trans.run("MATCH (a:profile {userName: $x}) RETURN a", Values.parameters("x", userName));
				if(!first.hasNext()) {
					exit = new DbQueryStatus("Username not found", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return exit;
				}
//				checks if the friend user is an actual node in db
				StatementResult second = trans.run("MATCH (a:profile {userName: $y}) RETURN a", Values.parameters("y", frndUserName));
				if(!second.hasNext()) {
					exit = new DbQueryStatus("Friend username not found", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return exit;
				}
//				checks if the user is even following the "friend user"
				StatementResult third = trans.run("MATCH (a:profile {userName: $x}), (b:profile {userName: $y}) MATCH (a)-[r:follows]->(b) RETURN r", Values.parameters("x", userName, "y", frndUserName));
				if(!third.hasNext()) {
					exit = new DbQueryStatus("User not following the other user", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return exit;
				}
				String queryStr = "MATCH ((:profile {userName: $x})-[r:follows]->(:profile {userName: $y})) DELETE r";
				trans.run(queryStr, Values.parameters("x", userName, "y", frndUserName));
				trans.success();
			}
			session.close();
		}
		exit = new DbQueryStatus("OK", DbQueryExecResult.QUERY_OK);
		return exit;
	}

	/**
	 * Checks if user is a profile node in neo4j db
	 * Gets all the profile nodes the user is following
	 * Adds these nodes with their favorite songs into an list
	 */
	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		DbQueryStatus exit;
		try (Session session = driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				StatementResult first = trans.run("MATCH (a:profile {userName: $x}) RETURN a", Values.parameters("x", userName));
//				checks if the user is an actual node in db				
				if(!first.hasNext()) {
					exit = new DbQueryStatus("Username not found", DbQueryExecResult.QUERY_ERROR_GENERIC);
					return exit;
				}
//				returns all the nodes that the user was following
				String queryStr = "MATCH (p:profile {userName : $x})-[relation:follows]->(b:profile) RETURN (b)";
				StatementResult second = trans.run(queryStr, Values.parameters("x", userName));
			    JSONObject newobject = new JSONObject();
//			    iterates over all the nodes and adds all the liked nodes into this object
				while(second.hasNext()) {
					Record record = second.next();
				    List<Pair<String, Value>> values = record.fields();
				    String friendPlaylist = values.get(0).value().get("userName").asString() + "-favorites";
					String queryFriend = "MATCH ((:playlist {plName: $x})-[relation:includes]->(s:song)) RETURN s";
					StatementResult res = trans.run(queryFriend, Values.parameters("x", friendPlaylist));
					ArrayList<String> ids = new ArrayList<>();
					while(res.hasNext()) {
						Record songName = res.next();
					    List<Pair<String, Value>> val = songName.fields();
					    String songMongoId = val.get(0).value().get("songId").asString();
					    ids.add(songMongoId);
					}
					newobject.put(values.get(0).value().get("userName").asString(), ids);
				}
				exit = new DbQueryStatus("OK", DbQueryExecResult.QUERY_OK);
				exit.setData(newobject);
			}
		}
		return exit;
	}
}
