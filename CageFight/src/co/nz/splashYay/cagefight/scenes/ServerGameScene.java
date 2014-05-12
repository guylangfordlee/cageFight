package co.nz.splashYay.cagefight.scenes;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.andengine.engine.Engine;
import org.andengine.engine.camera.Camera;
import org.andengine.engine.handler.IUpdateHandler;
import org.andengine.entity.scene.background.Background;
import org.andengine.entity.sprite.Sprite;
import org.andengine.entity.util.FPSLogger;
import org.andengine.extension.physics.box2d.FixedStepPhysicsWorld;
import org.andengine.extension.physics.box2d.PhysicsConnector;
import org.andengine.extension.physics.box2d.PhysicsFactory;
import org.andengine.extension.physics.box2d.util.Vector2Pool;
import org.andengine.extension.tmx.TMXTile;
import org.andengine.input.touch.TouchEvent;
import org.andengine.ui.activity.BaseGameActivity;
import org.andengine.util.math.MathUtils;

import android.speech.SpeechRecognizer;
import co.nz.splashYay.cagefight.EntityState;
import co.nz.splashYay.cagefight.GameData;
import co.nz.splashYay.cagefight.SceneManager;
import co.nz.splashYay.cagefight.Team.ALL_TEAMS;
import co.nz.splashYay.cagefight.entities.Base;
import co.nz.splashYay.cagefight.entities.Creep;
import co.nz.splashYay.cagefight.entities.Entity;
import co.nz.splashYay.cagefight.entities.Player;
import co.nz.splashYay.cagefight.entities.Tower;
import co.nz.splashYay.cagefight.network.Client;
import co.nz.splashYay.cagefight.network.InFromClientListener;
import co.nz.splashYay.cagefight.network.ServerCheckListener;
import co.nz.splashYay.cagefight.network.UDPServer;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.FixtureDef;

public class ServerGameScene extends GameScene {
	// networking
	private SceneManager sceneManager;	
	private InFromClientListener iFCL;
	//private OutToClientListener oTCL;
	private ServerCheckListener sCL;
	UDPServer udp;
	
	private int bugCounterTileIsNull = 0;
	
	

	
	
	
	public ServerGameScene(BaseGameActivity act, Engine eng, Camera cam, SceneManager sceneManager) {
		this.activity = act;
		this.engine = eng;
		this.camera = cam;		
		this.sceneManager = sceneManager;
	}

	

	public void createScene() {
		gameData = new GameData();
		this.engine.registerUpdateHandler(new FPSLogger());
		this.setBackground(new Background(0, 125, 58));
		this.phyWorld = new FixedStepPhysicsWorld(30, 30, new Vector2(0, 0), false);		
		this.registerUpdateHandler(phyWorld);
		
		
		
		setUpMap();
		setUpHUD();
		setUpBasesTowersAndAIunits();

		iFCL = new InFromClientListener(gameData, this);
		//oTCL = new OutToClientListener(gameData, this);
		sCL = new ServerCheckListener();
		udp = new UDPServer(gameData);
		iFCL.start();
		//oTCL.start();
		sCL.start();
		udp.start();
		
		player = new Player("", gameData.getUnusedID(), 100, 1, gameData.getTeam(ALL_TEAMS.EVIL).getSpawnXpos(), gameData.getTeam(ALL_TEAMS.EVIL).getSpawnYpos(), ALL_TEAMS.EVIL);
		addEntityToGameDataObj(player);
		
		//game loop
		this.registerUpdateHandler(new IUpdateHandler() {
			@Override
			public void onUpdate(float pSecondsElapsed) {
				
				processServerPlayerControls();
				processEntityActions();
				updateTargetMarker();
				
				//oTCL.updateClients();
				
				updateValueBars();
				

			}

			@Override
			public void reset() {
			}
		});
		//end game loop
		
		
	}
	
	
	
	private void processServerPlayerControls() {
		Player tempPlayer = (Player)gameData.getEntityWithId(player.getId());
		tempPlayer.setMovementX(playerCommands.getMovementX());
		tempPlayer.setMovementY(playerCommands.getMovementY());
		tempPlayer.setAttackCommand(playerCommands.isAttackCommand());
		tempPlayer.setTarget(gameData.getEntityWithId(playerCommands.getTargetID()));
	}
	
	private void processEntityActions(){
		
		
		HashMap temp = (HashMap) gameData.getEntities().clone();
		
		Iterator it = temp.entrySet().iterator();
		
		while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry) it.next();
			
			if (pairs.getValue() instanceof Player) {
				Player player = (Player) pairs.getValue();
				proccessPlayer(player);
				
			} else if (pairs.getValue() instanceof Base) {
				Base base = (Base) pairs.getValue();
				proccessBase(base);
			} else if (pairs.getValue() instanceof Tower) {
				
			} else if (pairs.getValue() instanceof Creep) {
				Creep ai = (Creep) pairs.getValue();
				proccessCreep(ai);
			}
			
		}
		
	}

	private void proccessCreep(Creep creep) {
		creep.checkState();
		
		creep.checkAndUpdateObjective(gameData);
		
		creep.getSprite().setRotation(creep.getDirectionToTarget());
		creep.setDirection(creep.getDirectionToTarget());

		creep.setXPos(creep.getSprite().getX());
		creep.setYPos(creep.getSprite().getY());
		checkTileEffect(creep);

		switch (creep.getState()) {
		case MOVING:
			
			creep.getBody().setActive(true);
			final Body creepBody = creep.getBody();
			
			float x = creep.getXdirectionToTarget();
			float y = creep.getYdirectionToTarget();
			
			
			
			
			final Vector2 velocity = Vector2Pool.obtain((x / 20 )%15, (y /20)%15);

			creepBody.setLinearVelocity(velocity);
			Vector2Pool.recycle(velocity);
			
					
			break;
		case IDLE:
			creep.stopEntity();
			break;
		case ATTACKING:
			creep.stopEntity();
			break;
		case DEAD:
			creep.stopEntity();
			if (creep.isAlive()) {
				creep.killCreep();
				sceneManager.getSoundManager().playRandomDeathSound();
			}	
			
			break;

		default:
			break;
		}
	}
	
	private void proccessBase(Base base) {
		base.checkState();
	}
	
	private void proccessPlayer(Player player) {		
		
		player.checkState();// checks and updates the players state
		
		player.setXPos(player.getSprite().getX());// set player position(in data) to the sprites position.
		player.setYPos(player.getSprite().getY());
		
		checkTileEffect(player);
		
		
		if (player.getPlayerState() == EntityState.MOVING) {
			player.getBody().setActive(true);
			final Body playerBody = player.getBody();
			final Vector2 velocity = Vector2Pool.obtain(player.getMovementX() * player.getSpeed(), player.getMovementY() * player.getSpeed());
			playerBody.setLinearVelocity(velocity);
			Vector2Pool.recycle(velocity);
			

			if (player.getMovementX() != 0 && player.getMovementY() != 0) {
				float direction = MathUtils.radToDeg((float) Math.atan2(player.getMovementX(), -player.getMovementY()));
				player.getSprite().setRotation(direction);
				player.setDirection(direction);
			}
		}  else {
			//stops players from moving
			player.stopEntity();
			
			if (player.getPlayerState() == EntityState.ATTACKING) {
				if (player.getTarget() != null && System.currentTimeMillis() >= (player.getLastAttackTime() + player.getAttackCoolDown())  ) {
					
					double distanceSqr = Math.pow((player.getTarget().getCenterXpos() - player.getCenterXpos()), 2) + Math.pow((player.getTarget().getCenterYpos() - player.getCenterYpos()), 2);
					double distance = Math.sqrt(distanceSqr);
					
					if(distance < 150)
					{
						player.attackTarget();
						player.setLastAttackTime(System.currentTimeMillis());
						sceneManager.getSoundManager().playRandomAttackSound();
					}
				}
				
				
				
			} else if (player.getPlayerState() == EntityState.IDLE) {
				//do nothing
				
				
			} else if (player.getPlayerState() == EntityState.DEAD) {
				//kill the player and check if when to respawn
				if (player.isAlive()) {
					player.killPlayer();
					sceneManager.getSoundManager().playRandomDeathSound();
				}	else {
					if (player.getRespawnTime() <= System.currentTimeMillis()) {
						player.respawn();
					}
				}
			}			
			
			
		}		
	}
	
	private void checkTileEffect(Entity entity) {
		final TMXTile tmxTile = mTMXTiledMap.getTMXLayers().get(12).getTMXTileAt(entity.getCenterXpos(), entity.getCenterYpos());
		
		if (tmxTile != null && tmxTile.getGlobalTileID() != 0) {
			try {
				if (tmxTile.getTMXTileProperties(mTMXTiledMap).containsTMXProperty("badHeal", "true")) {
					if (entity.getTeam() == ALL_TEAMS.EVIL) {
						entity.healEntity(1);
					} else {
						entity.setSpeed(2);
						//damage the player
					}
					
					
				} else if (tmxTile.getTMXTileProperties(mTMXTiledMap).containsTMXProperty("goodHeal", "true")) {
					if (entity.getTeam() == ALL_TEAMS.GOOD) {
						entity.healEntity(1);
					} else {
						entity.setSpeed(2);
						//damage the player
					}
				} 
			} catch (NullPointerException np) {
				
				bugCounterTileIsNull++;
				System.err.println("Null pointer on the tile again : " + bugCounterTileIsNull + " Times");
				
			}
			
			
		} else { // is not on an effecting tile, reverse any effects on the player
			entity.setSpeed(10);
		}
	}
	
	private void setUpBasesTowersAndAIunits(){
		Base team1Base = new Base(2750, 770, 10, 10, gameData.getUnusedID(), ALL_TEAMS.GOOD);
		addEntityToGameDataObj(team1Base);	
		Base team2Base = new Base(898, 770, 10, 10, gameData.getUnusedID(), ALL_TEAMS.EVIL);
		addEntityToGameDataObj(team2Base);
		
		Tower tower1 = new Tower(2180, 838, 10, 10, gameData.getUnusedID(), ALL_TEAMS.GOOD);
		addEntityToGameDataObj(tower1);	
		Tower tower2 = new Tower(1474, 838, 10, 10, gameData.getUnusedID(), ALL_TEAMS.EVIL);
		addEntityToGameDataObj(tower2);
		
		
		for (int i = 0; i < 2; i++) {
			Creep unit1 = new Creep(gameData.getUnusedID(), 10, 10, gameData.getTeam(ALL_TEAMS.GOOD).getSpawnXpos(), gameData.getTeam(ALL_TEAMS.GOOD).getSpawnYpos(), ALL_TEAMS.GOOD);
			addEntityToGameDataObj(unit1);	
			Creep unit2 = new Creep(gameData.getUnusedID(), 10, 10, gameData.getTeam(ALL_TEAMS.EVIL).getSpawnXpos(), gameData.getTeam(ALL_TEAMS.EVIL).getSpawnYpos(), ALL_TEAMS.EVIL);
			addEntityToGameDataObj(unit2);
		}
		
	}
	
	
	
	
	public void addEntityToGameDataObj(Entity newEntity) {
		/*
		boolean done = false;
		do{
			if(done){
				
			}
		} while (!done) ;
		
		*/
			
			
		if (newEntity != null) {
			if (newEntity instanceof Player) {
				Player newPlayer = (Player) newEntity;
				gameData.addPlayer(newPlayer);				
				Sprite tempS = new Sprite(newPlayer.getXPos(), newPlayer.getYPos(), playerTextureRegion, this.engine.getVertexBufferObjectManager()) {
					@Override
					public boolean onAreaTouched(final TouchEvent pSceneTouchEvent, final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
						setTarget(this);						
						return true;
					}
				};
				registerTouchArea(tempS);
				setTouchAreaBindingOnActionDownEnabled(true);				
				
				final FixtureDef playerFixDef = PhysicsFactory.createFixtureDef(1, 0f, 0.5f);
				newPlayer.setBody(PhysicsFactory.createCircleBody(phyWorld, tempS, BodyType.DynamicBody, playerFixDef));				
				phyWorld.registerPhysicsConnector(new PhysicsConnector(tempS, newPlayer.getBody(), true, false));
				this.attachChild(tempS);			
				newPlayer.setSprite(tempS);			
				
				if (newPlayer.getId() == player.getId()) {
					camera.setChaseEntity(tempS);
				}				
				
			} else if (newEntity instanceof Base) {
				Base newBase = (Base) newEntity;
				gameData.addEntity(newBase);
				FixtureDef baseFix = PhysicsFactory.createFixtureDef(0, 0f, 0f);
				Sprite baseS = new Sprite(newBase.getXPos(), newBase.getYPos(), baseTextureRegion, this.engine.getVertexBufferObjectManager()) {
					@Override
					public boolean onAreaTouched(final TouchEvent pSceneTouchEvent, final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
						setTarget(this);		
						
						return true;
					}
				};
				registerTouchArea(baseS);
				setTouchAreaBindingOnActionDownEnabled(true);
				newBase.setSprite(baseS);
				newBase.setBody(PhysicsFactory.createBoxBody(phyWorld, baseS, BodyType.StaticBody, baseFix));
				this.attachChild(baseS);
				
				
				
			} else if (newEntity instanceof Tower) {
				Tower newTower = (Tower) newEntity;
				gameData.addEntity(newTower);
				FixtureDef baseFix = PhysicsFactory.createFixtureDef(0, 0f, 0f);
				Sprite towerS = new Sprite(newTower.getXPos(), newTower.getYPos(), towerTextureRegion, this.engine.getVertexBufferObjectManager()) {
					@Override
					public boolean onAreaTouched(final TouchEvent pSceneTouchEvent, final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
						setTarget(this);		
						
						return true;
					}
				};
				registerTouchArea(towerS);
				setTouchAreaBindingOnActionDownEnabled(true);
				newTower.setSprite(towerS);
				newTower.setBody(PhysicsFactory.createBoxBody(phyWorld, towerS, BodyType.StaticBody, baseFix));
				this.attachChild(towerS);
			
			} else if (newEntity instanceof Creep) {
				Creep newAIunit = (Creep) newEntity;
				gameData.addEntity(newAIunit);
				Sprite tempS = new Sprite(newAIunit.getXPos(), newAIunit.getYPos(), AITextureRegion , this.engine.getVertexBufferObjectManager()) {
					@Override
					public boolean onAreaTouched(final TouchEvent pSceneTouchEvent, final float pTouchAreaLocalX, final float pTouchAreaLocalY) {
						setTarget(this);
						return true;
					}
				};
				registerTouchArea(tempS);
				setTouchAreaBindingOnActionDownEnabled(true);

				final FixtureDef AIFixDef = PhysicsFactory.createFixtureDef(1, 0f, 1f);
				newAIunit.setBody(PhysicsFactory.createCircleBody(phyWorld, tempS, BodyType.DynamicBody, AIFixDef));
				phyWorld.registerPhysicsConnector(new PhysicsConnector(tempS, newAIunit.getBody(), true, false));
				this.attachChild(tempS);
				newAIunit.setSprite(tempS);

			}
				
		}
		
	}



	public void addClient(Client client) {
		udp.addClient(client);		
	}

		
		
	
	
	

	
}
