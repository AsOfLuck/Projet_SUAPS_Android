package c.acdi.master.jderamaix.suaps;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    // Affichage des données sur le cours
    private int _capacity = 0;
    private String _duration = "00:00";

    public int capacity() { return _capacity; }
    public String duration() { return _duration; }

    // Élément principal de l'interface
    // Adaptateur de l'affichage des étudiants présents
    private StudentViewAdapter _adapter;
    public static final int BadgeRequest = 1;
    private static final String TAG = "MainActivity";

    // Intervalle utilisé pour la mise à jour périodique
    private final static int INTERVAL = 6;//60 * 3;

    //L'orgaisateur utilisé pour lancé la mise à jour de l'afichage à intervalle régulier.
    private ScheduledExecutorService organisateur = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> organisateurGerant;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _adapter = new StudentViewAdapter(this);



        RenseignementCapaciteHeure();
        //configureClass(5, 1, 20);

        // Initialisation du RecyclerView
        RecyclerView view = findViewById(R.id.affichageEtudiants);
        view.setHasFixedSize(true);
        view.setAdapter(_adapter);
        view.setLayoutManager(new LinearLayoutManager(this));

        // Implémentation de la suppression par swipe
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView view, RecyclerView.ViewHolder holder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder holder, int direction) {

                int numero_id = _adapter.get((int) holder.itemView.getTag()).id();

                String numero_id_chaine = "" + numero_id;

                Client client = ServiceGenerator.createService(Client.class);
                NomIDCarteEtudiant IDEtudiant = new NomIDCarteEtudiant(numero_id_chaine);
                Call<Void> call_Post = client.EnleverPersonne("" + numero_id_chaine,IDEtudiant);
                call_Post.enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.e("TAG", "Laréponse est véritable");
                            ReinitialiseAffichage();
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        ServiceGenerator.Message(MainActivity.this, TAG, t);
                    }
                });
            }
        }).attachToRecyclerView(view);

        //Créer un organisateur fixé à une intervalle de INTERVAL appellant AppelRun
        /*ScheduledFuture<?>*/ organisateurGerant = organisateur.scheduleAtFixedRate(AppelRun,0,INTERVAL,TimeUnit.SECONDS);
        organisateurGerant.cancel(true);


    }

    /**
     * Méthode invoqué quand MainActivity revient en premier plan,
     * soit quand l'utilisateur était sur une autre application
     * soit en revenant de RFIDActivity.
     * Lance la mise à jour de l'affichage
     */
    @Override
    public void onResume() {
        super.onResume();
        organisateurGerant = organisateur.scheduleAtFixedRate(AppelRun, 0, INTERVAL, TimeUnit.SECONDS);
        ReinitialiseAffichage();
    }


    /**
     * Méthode invoqué quand MainActivity va en second plan,
     * Arrête la mise à jour régulière de l'affichage
     */
    @Override
    protected void onPause(){
        super.onPause();
        organisateurGerant.cancel(true);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        organisateur.shutdown();
    }

    /**
     * Créer un runnable lançant la mise à jour de l'affichage
     */
    final Runnable AppelRun = new Runnable(){
        public void run() {
            ReinitialiseAffichage();
            RenseignementCapaciteHeure();
        }
    };

    /**
     * Méthode factorisant le code pour mettre à jour de l'affichage des présences.
     */
    private void _updateAttendance() {
        ((TextView) findViewById(R.id.affichageOccupation)).setText(
                getString(R.string.affichageOccupation, _adapter.getItemCount(), _capacity)
        );
    }

    /**
     * Méthode appliqué quand le bouton boutonBadge est cliqué.
     * Lance l'activité permettant de badger
     * @param view
     */
    public void Badger(View view) {
        Intent intent = new Intent(this, RFIDActivity.class);
        //Changer le startactivityforresult ==< on attend plus de result
        startActivityForResult(intent, BadgeRequest);
    }

    /**
     * Méthode pour lancer le dialogue d'ajout manuel.
     * Il est un callback invoqué par le bouton R.id.ajouterEtudiant.
     */
    public void ajouterEtudiant(View view) {
        new AddStudentDialog().show(getSupportFragmentManager(), "ajoutEtudiant");
    }

    /**
     * Méthode pour ajouter un étudiant manuellement à la séance.
     * Crée le client et un receptacle de la méthode permettant l'intéraction voulue avec la base de données
     * Puis applique la méthode asynchronement et si le résultat est réussi, réinitialise l'affichage
     * @param firstName Le prénom de l'étudiant à ajouter.
     * @param lastName  Le nom de l'étudiant à ajouter.
     */
    public void addStudent(String firstName, String lastName) {
        //Creer le client permettant d'interargir avec la base de données
        Client client = ServiceGenerator.createService(Client.class);

        //Créer un objet de classe PersonneNomPrenom utilisé pour stocker les informations envoyé par la requête à la base de données

        PersonneNomPrenom etudiant = new PersonneNomPrenom(lastName,firstName);

        //Créer le receptacle de la méthode voulue à partie de client
        //EnvoieNom prend en paramètre le string correspondant au nom de l'étudiant et une instance de Task

        Call<ReponseRequete> call_Post = client.EnvoieNom(etudiant.getNom(),etudiant.getPrenom());

        //Applique la requête à la base de données de façon asynchrone
        call_Post.enqueue(new Callback<ReponseRequete>() {
            @Override
            //Si la requête est arrivé jusqu'à la base de données
            public void onResponse(Call<ReponseRequete> call, Response<ReponseRequete> reponse) {
                //Test si la requête c'est bien ReponseRequetepassé
                if (reponse.isSuccessful()) {
                    Toast.makeText(MainActivity.this, reponse.body().getReponse() , Toast.LENGTH_SHORT).show();
                    //Toast.makeText(MainActivity.this, String.format("Le corps de task est : %s   ", String.valueOf(response.code())), Toast.LENGTH_SHORT).show();
                    ReinitialiseAffichage();
                } else {
                    Toast.makeText(MainActivity.this, "probleme petit", Toast.LENGTH_SHORT).show();
                    //Toast.makeText(MainActivity.this, String.format("Response is %s ", String.valueOf(response.code())), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            //Si la requête n'est pas arrivé jusqu'à la base de données
            public void onFailure(Call<ReponseRequete> call, Throwable t) {
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }

    /**
     * Méthode vidant la structure utilisée pour l'affichage des participants puis rajoutant
     * dedans ceux obtenues de la base de données, permet aussi de mettre à l'heure leur temps passé dans la salle.
     *
     * Créer le client et le réceptacle de la méthode permettant l'intéraction voulue avec la base de données puis applique la méthode asynchronement
     * Si un résultat est obtenue de la base de données, vérifie si il est non null et non vide(on veut quelquechose)
     * Puis remplace l'affichage actuelle par celui obtenue à partir de la base de données.
     */
    public void ReinitialiseAffichage() {

        //Créer le client permettant d'intérargir avec la base de données
        Client client = ServiceGenerator.createService(Client.class);

        //Utilise la méthode du client pour créer la requête permettant l'interaction avec la base de données
        //RecoitPersonnes ne prend pas de paramètre
        Call<List<ModeleEtudiant>> methodeCall = client.RecoitPersonnes();

        //Applique la requête à la base de données de façon asynchrone
        methodeCall.enqueue(new Callback<List<ModeleEtudiant>>() {
            @Override
            //Si la requête est arrivé jusqu'à la base de données
            public void onResponse(Call<List<ModeleEtudiant>> call, Response<List<ModeleEtudiant>> response) {

                Log.e(TAG,"rafraichissement");

                //Prend la partie de la reponse contenant les données voulues
                List<ModeleEtudiant> etudiantList = response.body();
                //Test si le conteneur de données est null
                if (!(etudiantList == null)) {
                    if (!etudiantList.isEmpty()) {
                        // Construire un ArrayList d'entrées...
                        ArrayList<StudentEntry> dataset = new ArrayList<>();
                        //... et y ajouter tous les étudiants obtenue de la base de données ...
                        Iterator<ModeleEtudiant> i = etudiantList.iterator();
                        do {
                            ModeleEtudiant etudiant = i.next();
                            dataset.add(new StudentEntry(
                                    getResources().getString(R.string.affichageNomEtudiant, etudiant.getNom(), etudiant.getPrenom()),
                                    etudiant.getDuree(),
                                    etudiant.getNo_etudiant()
                            ));
                        } while (i.hasNext());
                        //... pour mettre à jour l'adaptateur de manière atomique
                        _adapter.dataset(dataset);
                        _updateAttendance();
                    }
                } else {
                    Log.e(TAG,"La réponse obtenue est null, il y a une erreur (différences de typage avec a base de données ou autres)");
                }
            }

            //Si la requête n'est pas arrivé jusqu'à la base de données
            @Override
            public void onFailure(Call<List<ModeleEtudiant>> call, Throwable t) {
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.config_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.configurerCours:
                configurerCours(null);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Méthode pour lancer le dialogue de configuration de classe.
     */
    public void configurerCours(View view) {
        new ConfigDialog().show(getSupportFragmentManager(),"configClasse");
    }

    /**
     * Méthode permettant de changer la capacité et le temps minimum de la séance.
     * Elle est destinée à être utilisée à la place des setters déclarées en haut.
     *
     * @param capacity       La nouvelle capacité de la séance
     * @param minimumHours   Le nombre d'heures dans le nouveau temps minimum de la séance
     * @param minimumMinutes Le nombre de minutes dans le nouveau temps minimum de la séance
     */
    public void configureClass(int capacity, int minimumHours, int minimumMinutes) {
        _capacity = capacity;
        _duration = getString(R.string.affichageTemps, minimumHours, minimumMinutes);
        ((TextView) findViewById(R.id.affichageCapacite)).setText(
                getString(R.string.affichageCapacite, _capacity));
        _updateAttendance();
        ((TextView) findViewById(R.id.affichageTempsMinimum)).setText(_duration);
    }

    public void RenseignementCapaciteHeure() {

        Client client = ServiceGenerator.createService(Client.class);

        Call<List<AuaListeSeance>> call_Get  = client.RecoitParametre();

        call_Get.enqueue(new Callback<List<AuaListeSeance>>() {
            @Override
            public void onResponse(Call<List<AuaListeSeance>> call, Response<List<AuaListeSeance>> response) {
                if(response.isSuccessful()){

                    List<AuaListeSeance> listeSeance = response.body();

                    if(!listeSeance.isEmpty()) {
                        AuaListeSeance seance = listeSeance.get(0);

                        int minimum_heure = Integer.parseInt(seance.getTempsSeance().substring(0, 2));
                        int minimum_minute = Integer.parseInt(seance.getTempsSeance().substring(3, 5));

                        configureClass(Integer.parseInt(seance.getLimitePersonnes()), minimum_heure, minimum_minute);
                    }
                } else {
                    Log.e(TAG, "Get des paramètres non réussi");
                }
            }

            @Override
            public void onFailure(Call<List<AuaListeSeance>> call, Throwable t) {
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });

    }



    /**
     * Méthode pour envoyer à la base de données le changement des paramètres de temps minimum et de capacité de la séance
     *
     * Créer les strings équivalent du temps et de la capacité,
     * Créer l'objet de classe AuaListeSeance
     * Créer le client permettant d'intéragir avec la base de données
     * Utilise la méthode du client permettant l'interaction voulue avec la base de données
     * EnvoieTempsCapactie prend en paramètre une partie de l'URL et l'objet de classe AuaListeSeance contenant les données à envoyé.
     * Applique l'envoie de données à la base de données de façon asyncrone
     */
    public void ModificationCapaciteHeure(final int capacity, final int minimumHours, final int minimumMinutes) {

        //Créer les strings équivalent du temps et de la capacité
        String capacite = getString(R.string.affichageCapacite, capacity);
        String temps = getString(R.string.affichageTemps, minimumHours, minimumMinutes);


        //Créer le client permettant d'intéragir avec la base de données
        Client client = ServiceGenerator.createService(Client.class);

        //Utilise la méthode du client pour créer la requête permettant l'interaction voulue avec la base de données
        //EnvoieTempsCapactie prend en paramètre une partie de l'URL et l'objet de classe AuaListeSeance contenant les données à envoyé.

        Call<ReponseRequete> call_Post = client.EnvoieTempsCapacite(capacite, temps,"1");

        //Applique la requête à la base de données de façon asynchrone
        call_Post.enqueue(new Callback<ReponseRequete>() {
            @Override
            //Si la requête est arrivé jusqu'à la base de données
            public void onResponse(Call<ReponseRequete> call, Response<ReponseRequete> reponse) {
                int statusCode = reponse.code();
                if (reponse.isSuccessful()) {
                    Toast.makeText(MainActivity.this, reponse.body().getReponse() , Toast.LENGTH_SHORT).show();
                    configureClass(capacity,minimumHours,minimumMinutes);
                } else {
                    Log.e(TAG, "status Code: " + statusCode);
                }
            }

            @Override
            //Si la requête n'arrive pas jusqu'à la base de données
            public void onFailure(Call<ReponseRequete> call, Throwable t) {
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }
}
