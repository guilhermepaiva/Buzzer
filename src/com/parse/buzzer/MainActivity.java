package com.parse.buzzer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap.CancelableCallback;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.parse.FindCallback;
import com.parse.ParseACL;
import com.parse.ParseException;
import com.parse.ParseGeoPoint;
import com.parse.ParseQuery;
import com.parse.ParseQueryAdapter;
import com.parse.ParseUser;
import com.parse.SaveCallback;

public class MainActivity extends FragmentActivity implements LocationListener,
		GooglePlayServicesClient.ConnectionCallbacks,
		GooglePlayServicesClient.OnConnectionFailedListener {

	/*
	 * define um request code pra enviar pro Google Play, esse código é retornado em  Activity.onActivityResult
	 */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

	/*
	 * constantes pra parâmetros de localização
	 */
	// Milisegundos por segundo
	private static final int MILLISECONDS_PER_SECOND = 1000;

	// o intervalo de update
	private static final int UPDATE_INTERVAL_IN_SECONDS = 5;

	// um intervalo rápido
	private static final int FAST_CEILING_IN_SECONDS = 1;

	// intervalo de update em milisegundos
	private static final long UPDATE_INTERVAL_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
			* UPDATE_INTERVAL_IN_SECONDS;

	// um update rápido (teto), usando quando o app está visível
	private static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS = MILLISECONDS_PER_SECOND
			* FAST_CEILING_IN_SECONDS;

	/*
	 * constantes pra manipular os resultados dos locais
	 */
	// conversão de pés para metros
	private static final float METERS_PER_FEET = 0.3048f;

	// conversão de km pra metros
	private static final int METERS_PER_KILOMETER = 1000;

	// offset inicial pra calcular os limites do mapa
	private static final double OFFSET_CALCULATION_INIT_DIFF = 1.0;

	// precisão pra caulular os limites do mapa
	private static final float OFFSET_CALCULATION_ACCURACY = 0.01f;

	// máximo de resultados retornado por uma Parse query
	private static final int MAX_POST_SEARCH_RESULTS = 20;

	// máximo de ocorrências buscadas no mapa (o raio) em km
	private static final int MAX_POST_SEARCH_DISTANCE = 100;

	/*
	 * outras váriaveis que foram necessárias
	 */
	// Map fragment
	private SupportMapFragment map;

	// representa o círculo ao redor do mapa
	private Circle mapCircle;

	// campos pro mapa 
	private float radius;
	private float lastRadius;

	// campos pra ajudar o processamento do mapa e as mudanças de local
	private final Map<String, Marker> mapMarkers = new HashMap<String, Marker>();
	private int mostRecentMapUpdate = 0;
	private boolean hasSetUpInitialLocation = false;
	private String selectedObjectId;
	private Location lastLocation = null;
	private Location currentLocation = null;

	// Um request pra conectar ao Location Services
	private LocationRequest locationRequest;

	// armazena a instanciação atual do local do usuário nesse objeto
	private LocationClient locationClient;

	// Adapter para o Parse query
	private ParseQueryAdapter<BuzzerOccurrence> occurrences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		radius = Application.getSearchDistance();
		lastRadius = radius;
		setContentView(R.layout.activity_main);

		// cria/requisita uma nova localização global
		locationRequest = LocationRequest.create();

		// seta o intervalo de atualização
		locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

		// usa alta precisão
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		// seta o intervalo de 'teto' pra um minuto
		locationRequest
				.setFastestInterval(FAST_INTERVAL_CEILING_IN_MILLISECONDS);

		// cria um novo location client
		locationClient = new LocationClient(this, this, this);

		// seta uma query customizada
		ParseQueryAdapter.QueryFactory<BuzzerOccurrence> factory = new ParseQueryAdapter.QueryFactory<BuzzerOccurrence>() {
			public ParseQuery<BuzzerOccurrence> create() {
				Location myLoc = (currentLocation == null) ? lastLocation
						: currentLocation;
				ParseQuery<BuzzerOccurrence> query = BuzzerOccurrence.getQuery();
				query.include("user");
				query.orderByDescending("createdAt");
				query.whereWithinKilometers("location",
						geoPointFromLocation(myLoc), radius * METERS_PER_FEET
								/ METERS_PER_KILOMETER);
				query.setLimit(MAX_POST_SEARCH_RESULTS);
				return query;
			}
		};

		// seta o query adapter
		occurrences = new ParseQueryAdapter<BuzzerOccurrence>(this, factory) {
			@Override
			public View getItemView(BuzzerOccurrence occurrence, View view,
					ViewGroup parent) {
				if (view == null) {
					view = View.inflate(getContext(),
							R.layout.buzzer_occurrence_item, null);
				}
				TextView contentView = (TextView) view
						.findViewById(R.id.contentView);
				TextView usernameView = (TextView) view
						.findViewById(R.id.usernameView);
				contentView.setText(occurrence.getText());
				usernameView.setText(occurrence.getUser().getUsername());
				return view;
			}
		};

		// disabilita o load automático quando o adapter está vinculado a uma view
		occurrences.setAutoload(false);

		// disabilita paginação, isso vai ser gerenciado pelo limite de querys
		occurrences.setPaginationEnabled(false);

		// vincula uma query adapter em uma view
		ListView postsView = (ListView) this.findViewById(R.id.occurrencesView);
		postsView.setAdapter(occurrences);

		// seta o handler pra um item selecionado
		postsView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				final BuzzerOccurrence item = occurrences.getItem(position);
				selectedObjectId = item.getObjectId();
				map.getMap().animateCamera(
						CameraUpdateFactory.newLatLng(new LatLng(item
								.getLocation().getLatitude(), item
								.getLocation().getLongitude())),
						new CancelableCallback() {
							public void onFinish() {
								Marker marker = mapMarkers.get(item
										.getObjectId());
								if (marker != null) {
									marker.showInfoWindow();
								}
							}

							public void onCancel() {
							}
						});
				Marker marker = mapMarkers.get(item.getObjectId());
				if (marker != null) {
					marker.showInfoWindow();
				}
			}
		});

		// seta o map fragment
		map = (SupportMapFragment) this.getSupportFragmentManager()
				.findFragmentById(R.id.map);
		// habilita o current location  como sendo o "ponto azul"
		map.getMap().setMyLocationEnabled(true);
		// seta o handler pra quando a camera mudar
		map.getMap().setOnCameraChangeListener(new OnCameraChangeListener() {
			public void onCameraChange(CameraPosition position) {
				// quando a camera mudar, atualiza a query
				doMapQuery();
			}
		});

		// seta o handler pra o botão de alertar
		Button postButton = (Button) findViewById(R.id.occurrenceButton);
		postButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// só deixa registrar uma ocorrência se tiver uma localização
				Location myLoc = (currentLocation == null) ? lastLocation
						: currentLocation;
				if (myLoc == null) {
					Toast.makeText(
							MainActivity.this,
							"Por favor, tente novamente quando sua localização aparecer no mapa.",
							Toast.LENGTH_LONG).show();
					return;
				}
				final ParseGeoPoint myPoint = geoPointFromLocation(myLoc);
				// cria um builder quando uma nova ocorrência é digitada
				AlertDialog.Builder alert = new AlertDialog.Builder(
						MainActivity.this);
				alert.setTitle("Nova Ocorrência");
				final EditText input = new EditText(MainActivity.this);
				input.setInputType(InputType.TYPE_CLASS_TEXT
						| InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
				alert.setView(input);
				// manipula o input do dialog
				alert.setPositiveButton("Ocorrência",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								// Cria uma ocorrência.
								BuzzerOccurrence occurrence = new BuzzerOccurrence();
								// seta o local para o local do usuário
								occurrence.setLocation(myPoint);
								occurrence.setText(input.getText().toString());
								occurrence.setUser(ParseUser.getCurrentUser());
								ParseACL acl = new ParseACL();
								// permite public read access
								acl.setPublicReadAccess(true);
								occurrence.setACL(acl);
								// Save the post
								occurrence.saveInBackground(new SaveCallback() {
									@Override
									public void done(ParseException e) {
										// atualiza a list view do mapa (depois tirar, por equanto só é teste pra ver como tá salvando)
										doListQuery();
										doMapQuery();
									}
								});
							}
						});
				alert.setNegativeButton("Cancelar",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								// faz nada.
							}
						});
				alert.create().show();
			}
		});
	}

	/*
	 *chamado quando a activity não está mais visívell. para com os updates e disconecta.
	 */
	@Override
	public void onStop() {
		// se o cliente está conectado
		if (locationClient.isConnected()) {
			stopPeriodicUpdates();
		}

		// depois quer disconnect() é chamado, o cliente já era
		locationClient.disconnect();

		super.onStop();
	}

	/*
	 * chamdo quando a Activity é reinicializada, mesmo depois que se torna visível.
	 */
	@Override
	public void onStart() {
		super.onStart();

		// se conecta com o location services do cliente
		locationClient.connect();
	}

	/*
	 * chamado quando a Activity está em onResume. atualiza a view.
	 */
	@Override
	protected void onResume() {
		super.onResume();

		// pega a última busca da distância que tava na configuração (o lance do raio que vai servir pra configurar o alerta)
		radius = Application.getSearchDistance();
		// checa se a última localização salva está na cache e se está disponível
		if (lastLocation != null) {
			LatLng myLatLng = new LatLng(lastLocation.getLatitude(),
					lastLocation.getLongitude());
			// se a busca pela preferência de ratio foi modificada, então tem que mudar o limite do mapa
			if (lastRadius != radius) {
				updateZoom(myLatLng);
			}
			// atualiza o círculo do mapa
			updateCircle(myLatLng);
		}
		// salva o raio atual
		lastRadius = radius;
		// Query pra o último dado atualizado nas views.
		doMapQuery();
		doListQuery();
	}

	/*
	 * manipula os resultados retornados pra essa activity por outras activities inicializadas com
	 * startActivityForResult(). Em particular, o método onConnectionFailed()
	 * em LocationUpdateRemover e LocationUpdateRequester pode chamar
	 * startResolutionForResult() que inicia a activity que mexe com algumas coisas relacionadas a erro do Google Play
	 * services. O resultado dessa chamada é retornado aqui pra onActivityResult.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		// escolhe o que fazer baseado no request code
		switch (requestCode) {

		// se o request code bate com o código que foi enviado pra onConnectionFailed
		case CONNECTION_FAILURE_RESOLUTION_REQUEST:

			switch (resultCode) {
			// se Google Play services resolveu a bronca..
			case Activity.RESULT_OK:

				if (Application.APPDEBUG) {
					// Log do resultado
					Log.d(Application.APPTAG,
							"Connected to Google Play services");
				}

				break;

			// se alguma outro resultado foi retornado pelo Google Play services
			default:
				if (Application.APPDEBUG) {
					// Log do resultado
					Log.d(Application.APPTAG,
							"Could not connect to Google Play services");
				}
				break;
			}

			// se algum outra request code foi recebida
		default:
			if (Application.APPDEBUG) {
				// um report pra o logcat que essa activity recebeu um requestCode desconhecido
				Log.d(Application.APPTAG,
						"Unknown request code received for the activity");
			}
			break;
		}
	}

	/*
	 * verifica se Google Play services está disponível antes de fazer um request
	 * 
	 * @return true se Google Play services está disponível, caso não esteja retorna false
	 */
	private boolean servicesConnected() {
		// checa se Google Play services está disponível
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(this);

		// se Google Play services estiver disponível
		if (ConnectionResult.SUCCESS == resultCode) {
			if (Application.APPDEBUG) {
				// usado pra o debug
				Log.d(Application.APPTAG, "Google play services available");
			}
			// Continua
			return true;
			// Google Play services são está disponível por algum motivo
		} else {
			// mosta um error dialog
			Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode,
					this, 0);
			if (dialog != null) {
				ErrorDialogFragment errorFragment = new ErrorDialogFragment();
				errorFragment.setDialog(dialog);
				errorFragment.show(getSupportFragmentManager(),
						Application.APPTAG);
			}
			return false;
		}
	}

	/*
	 * chamado por Location Services quando uma requisição pra conectar o cliente acaba com sucesso
	 * aqui, dá pra requisitar a localização atual ou iniciar aqueles updates periódicos
	 */
	public void onConnected(Bundle bundle) {
		if (Application.APPDEBUG) {
			Log.d("Connected to location services", Application.APPTAG);
		}
		currentLocation = getLocation();
		startPeriodicUpdates();
	}

	/*
	 * chamado pelo Location Services se a conexão pra localização do cliente cair por causa de algum erro
	 */
	public void onDisconnected() {
		if (Application.APPDEBUG) {
			Log.d("Disconnected from location services", Application.APPTAG);
		}
	}

	/*
	 * chamado por Location Services se a tentativa do Location Services falhar.
	 */
	public void onConnectionFailed(ConnectionResult connectionResult) {
		// Google Play services pode dar conta de alguns erros. se o erro tem solução, ele tenta mandar 
		// uma Intent iniciar o Google Play services que pode resuolver esse erro
		if (connectionResult.hasResolution()) {
			try {

				// inicia a activity que tenta resolver o erro (como dito acima)
				connectionResult.startResolutionForResult(this,
						CONNECTION_FAILURE_RESOLUTION_REQUEST);

			} catch (IntentSender.SendIntentException e) {

				if (Application.APPDEBUG) {
					// quando dá bronca com o Google Play se for cancelado o original PendingIntent
					Log.d(Application.APPTAG,
							"An error occurred when connecting to location services.",
							e);
				}
			}
		} else {

			// se nenhuma solução estiver disponível, mostra um dialog pra o usuário com o erro
			showErrorDialog(connectionResult.getErrorCode());
		}
	}

	/*
	 * reporta as atualizações pra a interface do usuário (UI)
	 */
	public void onLocationChanged(Location location) {
		currentLocation = location;
		if (lastLocation != null
				&& geoPointFromLocation(location).distanceInKilometersTo(
						geoPointFromLocation(lastLocation)) < 0.01) {
			// se a localização não mudou mais que 10 metros, ignore.
			return;
		}
		lastLocation = location;
		LatLng myLatLng = new LatLng(location.getLatitude(),
				location.getLongitude());
		if (!hasSetUpInitialLocation) {
			// Zoom pra localização atual
			updateZoom(myLatLng);
			hasSetUpInitialLocation = true;
		}
		// atualiza o indicador do raio do mapa
		updateCircle(myLatLng);
		doMapQuery();
		doListQuery();
	}

	/*
	 * em resposta a requisição pra começar as atualizações, envia uma request pra o Location Services
	 */
	private void startPeriodicUpdates() {
		locationClient.requestLocationUpdates(locationRequest, this);
	}

	/*
	 em resposta a requisição pra parar as atualizações, envia uma request pra o Location Services
	 */
	private void stopPeriodicUpdates() {
		locationClient.removeLocationUpdates(this);
	}

	/*
	 * pega a localização atual
	 */
	private Location getLocation() {
		// checa se o Google Play Services tá disponível
		if (servicesConnected()) {
			// pega a localização atual
			return locationClient.getLastLocation();
		} else {
			return null;
		}
	}

	/*
	 * seta a query pra atualizar a list view
	 */
	private void doListQuery() {
		Location myLoc = (currentLocation == null) ? lastLocation
				: currentLocation;
		// se a localização estiver disponível, carregue os dados
		if (myLoc != null) {
			// dá um refresh na list view com os novos dados baseado no update da localização dos dados.
			occurrences.loadObjects();
		}
	}

	/*
	 * seta query pra atualizar o map view
	 */
	private void doMapQuery() {
		final int myUpdateNumber = ++mostRecentMapUpdate;
		Location myLoc = (currentLocation == null) ? lastLocation
				: currentLocation;
		// se a localização não estiver disponível, limpe (tire) qualquer markers (pinos)
		if (myLoc == null) {
			cleanUpMarkers(new HashSet<String>());
			return;
		}
		final ParseGeoPoint myPoint = geoPointFromLocation(myLoc);
		// cria o mapa da query do Parse
		ParseQuery<BuzzerOccurrence> mapQuery = BuzzerOccurrence.getQuery();
		// seta alguns filtros pras queryes
		mapQuery.whereWithinKilometers("location", myPoint,
				MAX_POST_SEARCH_DISTANCE);
		mapQuery.include("user");
		mapQuery.orderByDescending("createdAt");
		mapQuery.setLimit(MAX_POST_SEARCH_RESULTS);
		// Kick off the query in the background
		mapQuery.findInBackground(new FindCallback<BuzzerOccurrence>() {
			@Override
			public void done(List<BuzzerOccurrence> objects, ParseException e) {
				if (e != null) {
					if (Application.APPDEBUG) {
						Log.d(Application.APPTAG,
								"An error occurred while querying for map posts.",
								e);
					}
					return;
				}
				/*
				 * assegura que estamos processando os resultados do update mais recente
				 *, no caso de ter mais que um em progresso.
				 */
				if (myUpdateNumber != mostRecentMapUpdate) {
					return;
				}
				// ocorrências pra mostrar no mapa
				Set<String> toKeep = new HashSet<String>();
				// loop dos resultados da consulta das ocorrências
				for (BuzzerOccurrence post : objects) {
					// adiciona essa ocorrência pra lista dos pinos do mapa
					toKeep.add(post.getObjectId());
					// verifica se existe um pino pra essa ocorrência (evitar duplicidade)
					Marker oldMarker = mapMarkers.get(post.getObjectId());
					// seta a localização do pino
					MarkerOptions markerOpts = new MarkerOptions()
							.position(new LatLng(post.getLocation()
									.getLatitude(), post.getLocation()
									.getLongitude()));
					// seta as propriedades do pino (marker) baseado no raio que foi definido
					if (post.getLocation().distanceInKilometersTo(myPoint) > radius
							* METERS_PER_FEET / METERS_PER_KILOMETER) {
						// verifica se tem um pino fora do raio
						if (oldMarker != null) {
							if (oldMarker.getSnippet() == null) {
								// se tiver um pino fora do range, então pula isso
								continue;
							} else {
								// atualiza os pinos que tão fora do raio dando um refresh
								oldMarker.remove();
							}
						}
						// mostra um peino vermelho com um título predefinido e sem balãozinho
						markerOpts = markerOpts
								.title(getResources().getString(
										R.string.post_out_of_range))
								.icon(BitmapDescriptorFactory
										.defaultMarker(BitmapDescriptorFactory.HUE_RED));
					} else {
						// verifica se tem algum pino no range
						if (oldMarker != null) {
							if (oldMarker.getSnippet() != null) {
								// se tem o pino, pula
								continue;
							} else {
								// agora o pino que tá dentro do raio precisa entrar no refresh
								oldMarker.remove();
							}
						}
						// mostra um pino verde pra uma ocorrência que foi colocada pelo usuário
						markerOpts = markerOpts
								.title(post.getText())
								.snippet(post.getUser().getUsername())
								.icon(BitmapDescriptorFactory
										.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
					}
					// adiciona um novo pino
					Marker marker = map.getMap().addMarker(markerOpts);
					mapMarkers.put(post.getObjectId(), marker);
					if (post.getObjectId().equals(selectedObjectId)) {
						marker.showInfoWindow();
						selectedObjectId = null;
					}
				}
				// limpa os pinos antigos.
				cleanUpMarkers(toKeep);
			}
		});
	}

	/*
	 * um helper pra limpar os pinos antigos
	 */
	private void cleanUpMarkers(Set<String> markersToKeep) {
		for (String objId : new HashSet<String>(mapMarkers.keySet())) {
			if (!markersToKeep.contains(objId)) {
				Marker marker = mapMarkers.get(objId);
				marker.remove();
				mapMarkers.get(objId).remove();
				mapMarkers.remove(objId);
			}
		}
	}

	/*
	 * um helper pra pegar a representação do Parse GEO point de um lugar
	 */
	private ParseGeoPoint geoPointFromLocation(Location loc) {
		return new ParseGeoPoint(loc.getLatitude(), loc.getLongitude());
	}

	/*
	 * mostra um círculo no mapa representadndo o raio de busca (vai servir pra entrar na config de onde quer ser alertado)
	 */
	private void updateCircle(LatLng myLatLng) {
		if (mapCircle == null) {
			mapCircle = map.getMap().addCircle(
					new CircleOptions().center(myLatLng).radius(
							radius * METERS_PER_FEET));
			int baseColor = Color.DKGRAY;
			mapCircle.setStrokeColor(baseColor);
			mapCircle.setStrokeWidth(2);
			mapCircle.setFillColor(Color.argb(50, Color.red(baseColor),
					Color.green(baseColor), Color.blue(baseColor)));
		}
		mapCircle.setCenter(myLatLng);
		mapCircle.setRadius(radius * METERS_PER_FEET); // converte o raio de pés pra metros
	}

	/*
	 * dá um zoom na área do mapa pra mostrar a área de interesse baseado no raio
	 */
	private void updateZoom(LatLng myLatLng) {
		// pega os limites do zoom
		LatLngBounds bounds = calculateBoundsWithCenter(myLatLng);
		// dá um zoom pra os limites do mapa
		map.getMap().animateCamera(
				CameraUpdateFactory.newLatLngBounds(bounds, 5));
	}

	/*
	 * métodos auxiliar que calcula o offset dos limites usados no zoom do mapa
	 */
	private double calculateLatLngOffset(LatLng myLatLng, boolean bLatOffset) {
		// o offset de retorno é inicializado com o default
		double latLngOffset = OFFSET_CALCULATION_INIT_DIFF;
		// seta o offset desejado em metros
		float desiredOffsetInMeters = radius * METERS_PER_FEET;
		// variável pra calcular a distância
		float[] distance = new float[1];
		boolean foundMax = false;
		double foundMinDiff = 0;
		// loop pelo offset
		do {
			// calcula a distância entre o ponto que se quer o o offset atual na direção da latitude ou longitude
			if (bLatOffset) {
				Location.distanceBetween(myLatLng.latitude, myLatLng.longitude,
						myLatLng.latitude + latLngOffset, myLatLng.longitude,
						distance);
			} else {
				Location.distanceBetween(myLatLng.latitude, myLatLng.longitude,
						myLatLng.latitude, myLatLng.longitude + latLngOffset,
						distance);
			}
			// compara a diferença atual com a desejada
			float distanceDiff = distance[0] - desiredOffsetInMeters;
			if (distanceDiff < 0) {
				// é necessário alcaçar a distância desejada
				if (!foundMax) {
					foundMinDiff = latLngOffset;
					// incrementa com o offset calculado
					latLngOffset *= 2;
				} else {
					double tmp = latLngOffset;
					// incrementa com o offset calculado, com um passo mais lento (menor)
					latLngOffset += (latLngOffset - foundMinDiff) / 2;
					foundMinDiff = tmp;
				}
			} else {
				// vai além da distância que se quer
				// decrementa o offset calculado
				latLngOffset -= (latLngOffset - foundMinDiff) / 2;
				foundMax = true;
			}
		} while (Math.abs(distance[0] - desiredOffsetInMeters) > OFFSET_CALCULATION_ACCURACY);
		return latLngOffset;
	}

	/*
	 * método auxiliar pra calcular os limites do zoom do mapa
	 */
	public LatLngBounds calculateBoundsWithCenter(LatLng myLatLng) {
		// cria os limites
		LatLngBounds.Builder builder = LatLngBounds.builder();

		// calcula os pontos leste/oeste que devem estar nos limites
		double lngDifference = calculateLatLngOffset(myLatLng, false);
		LatLng east = new LatLng(myLatLng.latitude, myLatLng.longitude
				+ lngDifference);
		builder.include(east);
		LatLng west = new LatLng(myLatLng.latitude, myLatLng.longitude
				- lngDifference);
		builder.include(west);

		// calcula os pontos norte/sul que devem estar nos limites
		double latDifference = calculateLatLngOffset(myLatLng, true);
		LatLng north = new LatLng(myLatLng.latitude + latDifference,
				myLatLng.longitude);
		builder.include(north);
		LatLng south = new LatLng(myLatLng.latitude - latDifference,
				myLatLng.longitude);
		builder.include(south);

		return builder.build();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate no menu; adiciona itens na actionbar se ela estiver presente.
		getMenuInflater().inflate(R.menu.main, menu);

		menu.findItem(R.id.action_settings).setOnMenuItemClickListener(
				new OnMenuItemClickListener() {
					public boolean onMenuItemClick(MenuItem item) {
						startActivity(new Intent(MainActivity.this,
								SettingsActivity.class));
						return true;
					}
				});
		return true;
	}

	/*
	 * mostra um dialog retornado pelo Google Play services pelo erro na conexão
	 */
	private void showErrorDialog(int errorCode) {
		// pega o error dialog do Google Play services
		Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode,
				this, CONNECTION_FAILURE_RESOLUTION_REQUEST);

		// se Google Play services pode ajudar com isso...
		if (errorDialog != null) {

			// cria um novo DialogFragment que mostra o error dialog
			ErrorDialogFragment errorFragment = new ErrorDialogFragment();

			// seta o dialog no DialogFragment
			errorFragment.setDialog(errorDialog);

			// mostra o error dialog no DialogFragment
			errorFragment.show(getSupportFragmentManager(), Application.APPTAG);
		}
	}

	/*
	 * define um DialogFragment pra mostrar o error dialog gerado em showErrorDialog.
	 */
	public static class ErrorDialogFragment extends DialogFragment {
		// campo Global que contem o error dialog
		private Dialog mDialog;

		/**
		 * connstrutor default constructor. seta o dialog field pra null
		 */
		public ErrorDialogFragment() {
			super();
			mDialog = null;
		}

		/*
		 * seta o dialog to mostrar
		 * 
		 * @param dialog An error dialog
		 */
		public void setDialog(Dialog dialog) {
			mDialog = dialog;
		}

		/*
		 * esse método deve retorna um Dialog pra o DialogFragment.
		 */
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			return mDialog;
		}
	}

}

