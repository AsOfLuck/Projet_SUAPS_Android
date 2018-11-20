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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okio.Buffer;
import okio.BufferedSink;
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
    // TAG est utilisé pour recherche plus facilement dans les logs.
    private static final String TAG = "MainActivity";

    // Intervalle utilisé pour la mise à jour périodique
    private final static int INTERVAL = 6;

    //L'orgaisateur et son gérant, utilisé pour lancer, stopper et supprimer la mise à jour de l'afichage à intervalle régulier.
    private ScheduledExecutorService organisateur = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> organisateurGerant;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        _adapter = new StudentViewAdapter(this);



        RenseignementCapaciteHeure();

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

                //Créé le client utilisé pour intérargir avec la base de données.
                ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);
                NumeroIDCarteEtudiant IDEtudiant = new NumeroIDCarteEtudiant(numero_id_chaine);

                //Créer le receptacle de la méthode voulue à partir de clientRequete
                //EnleverPersonne prend en paramètre le String et le NumeroIdCarteEtudiant correspondant à l'id de l'utilisateur à enlever.
                Call<Void> call_Post = clientRequete.EnleverPersonne(numero_id_chaine,IDEtudiant);

                // Implémentation de la suppression par swipe
                //Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack
                //Ici le traitement de CallBack est directement appliqué :
                //  onResponse si la requête est considérée réussite(Si une réponse http esr reçu).
                //  onFailure si la requête est considérée ratée.
                call_Post.enqueue(new Callback<Void>() {
                    @Override
                    //Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        //Test si la requête a réussi ( code http allant de 200 à 299).
                        if (response.isSuccessful()) {
                            Log.e("TAG", "La personne a été enlevée");
                            //Met à jour l'affichage.
                            ReinitialiseAffichage();
                        } else {
                            //Affiche le code de la reponse, soit le code http de la requête.
                            Log.e(TAG, "Status code : " + response.code());
                        }
                    }
                    @Override
                    /**
                     * Méthode étant appliqué losque des problèmes sont apparus lors de  :
                     *   - la connexion au serveur,
                     *   - la création de la requête,
                     *   - la transformation de la réponse en objet java (ne peut pas causer de problème ici, aucune réponse n'est attendu).
                     * @Param call : La requête provoquant le onFailure.
                     * @Param t    : objet contenant le message et le code d'erreur provoqué par la requête.
                     */
                    public void onFailure(Call<Void> call, Throwable t) {
                        //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                        ServiceGenerator.Message(MainActivity.this, TAG, t);
                    }
                });
            }
        }).attachToRecyclerView(view);

        //Créer un organisateur fixé à une intervalle de INTERVAL secondes appellant AppelRun.
        /*ScheduledFuture<?>*/ organisateurGerant = organisateur.scheduleAtFixedRate(AppelRun,0,INTERVAL,TimeUnit.SECONDS);
        organisateurGerant.cancel(true);


    }

    /**
     * Méthode invoqué quand MainActivity revient en premier plan,
     * soit quand l'utilisateur était sur une autre application
     * soit en revenant de RFIDActivity.
     * Lance la mise à jour de l'affichage et relance l'organisateur s'occupant de la mise à jour automatique des données.
     */
    @Override
    public void onResume() {
        super.onResume();
        //Toutes les INTERVAL secondes, applique la méthode AppelRun.
        organisateurGerant = organisateur.scheduleAtFixedRate(AppelRun, 0, INTERVAL, TimeUnit.SECONDS);
        ReinitialiseAffichage();
    }


    /**
     * Méthode invoqué quand MainActivity va en second plan,
     * Arrête la mise à jour automatique de l'affichage.
     */
    @Override
    protected void onPause(){
        super.onPause();
        organisateurGerant.cancel(true);
    }

    /**
     * Méthode invoqué quand l'activité est détruite,
     * Détruit l'organisateur.
     */
    @Override
    protected void onDestroy(){
        super.onDestroy();
        organisateur.shutdown();
    }

    /**
     * Créer un runnable lançant la mise à jour de l'affichage.
     */
    final Runnable AppelRun = new Runnable(){
        public void run() {
            ReinitialiseAffichage();
            RenseignementCapaciteHeure();
        }
    };

    /**
     * Méthode factorisant le code pour mettre à jour l'affichage des présences.
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
        startActivity(intent);
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
        //Créé le client permettant d'interargir avec la base de données
        ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);

        //Créer le receptacle de la méthode voulue à partir de clientRequetes
        //EnvoieNom prend en paramètre le nom et le prénom de l'utilisateur.
        Call<ReponseRequete> call_Post = clientRequete.EnvoieNom(lastName, firstName);

        //Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack
        //Ici le traitement de CallBack est directement appliqué :
        //  onResponse si la requête est considérée réussite(Si une réponse http esr reçu).
        //  onFailure si la requête est considérée ratée.
        call_Post.enqueue(new Callback<ReponseRequete>() {
            @Override
            //Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
            public void onResponse(Call<ReponseRequete> call, Response<ReponseRequete> reponse) {
                //Test si la requête a réussi ( code http allant de 200 à 299).
                if (reponse.isSuccessful()) {
                    Toast.makeText(MainActivity.this, reponse.body().getReponse() , Toast.LENGTH_SHORT).show();
                    //Toast.makeText(MainActivity.this, String.format("Le corps de task est : %s   ", String.valueOf(response.code())), Toast.LENGTH_SHORT).show();
                    ReinitialiseAffichage();
                } else {
                    //Affiche le code de la reponse, soit le code http de la requête.
                    Log.e(TAG,"Status code : " + reponse.code());
               }
            }

            @Override
            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java (ici un ReponseRequete).
             * @Param call : La requête provoquant le onFailure.
             * @Param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */
            public void onFailure(Call<ReponseRequete> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision
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
        ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);

        //Utilise la méthode du client pour créer la requête permettant l'interaction avec la base de données
        //RecoitPersonnes ne prend pas de paramètre
        Call<List<ModeleUtilisateur>> methodeCall = clientRequete.RecoitPersonnes();

        //Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack
        //Ici le traitement de CallBack est directement appliqué :
        //  onResponse si la requête est considérée réussite(Si une réponse http esr reçu).
        //  onFailure si la requête est considérée ratée.
        methodeCall.enqueue(new Callback<List<ModeleUtilisateur>>() {
            @Override
            //Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
            public void onResponse(Call<List<ModeleUtilisateur>> call, Response<List<ModeleUtilisateur>> response) {
                //Test si la requête a réussi ( code http allant de 200 à 299).
                if (response.isSuccessful()) {
                    //Prend la partie de la reponse contenant les données voulues
                    List<ModeleUtilisateur> etudiantList = response.body();
                    //Test si le conteneur de données est null
                    if (etudiantList != null) {
                        // Construire un ArrayList d'entrées...
                        ArrayList<StudentEntry> dataset = new ArrayList<>();
                        if (!etudiantList.isEmpty()) {
                            //... et y ajouter tous les étudiants obtenue de la base de données ...
                            Iterator<ModeleUtilisateur> i = etudiantList.iterator();
                            do {
                                ModeleUtilisateur etudiant = i.next();
                                dataset.add(new StudentEntry(
                                        getResources().getString(R.string.affichageNomEtudiant, etudiant.getNom(), etudiant.getPrenom()),
                                        etudiant.getDuree(),
                                        etudiant.getNo_etudiant()
                                ));
                            } while (i.hasNext());
                        }
                        //... pour mettre à jour l'adaptateur de manière atomique
                        _adapter.dataset(dataset);
                        _updateAttendance();
                    } else {
                        Log.e(TAG, "La liste d'utilisateur est vide.");
                    }
                } else {
                    //Affiche le code de la reponse, soit le code http de la requête.
                    Log.e(TAG,"Status code : " + response.code());
                }
            }

            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java (ici une liste de ModeleUtilisateur).
             * @Param call : La requête provoquant le onFailure.
             * @Param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */            @Override
            public void onFailure(Call<List<ModeleUtilisateur>> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
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

        //Créé le client à partir du ServiceGenerator, il sera utilisé pour intérargir avec la base de données.
        ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);

        //Utilise la méthode du client pour créer la requête permettant l'intéraction voulue avec la base de données.
        //RecoitParametre n'a pas besoin de paramètre.
        Call<List<AuaListeSeance>> call_Get  = clientRequete.RecoitParametre();

        //Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack
        //Ici le traitement de CallBack est directement appliqué :
        //  onResponse si la requête est considérée réussite(Si une réponse http esr reçu).
        //  onFailure si la requête est considérée ratée.
        call_Get.enqueue(new Callback<List<AuaListeSeance>>() {
            @Override
            //Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
            public void onResponse(Call<List<AuaListeSeance>> call, Response<List<AuaListeSeance>> response) {
                //Test si la requête a réussi (code http allant de 200 à 299).
                if(response.isSuccessful()){

                    //Prend la partie de response correspondant à l'objet envoyé par la base de données.
                    List<AuaListeSeance> listeSeance = response.body();

                    //Test si la liste de séance obtenu par la requête n'est pas vide.
                    if(!listeSeance.isEmpty()) {
                        //Prend la première valeur de la liste de séance, pour l'instant il n'y en a qu'une d'envoyée.
                        AuaListeSeance seance = listeSeance.get(0);

                        //Sépare le temps limite de la séance en deux parties, une pour les heures et l'autre pour les minutes.
                        int minimum_heure = Integer.parseInt(seance.getTempsSeance().substring(0, 2));
                        int minimum_minute = Integer.parseInt(seance.getTempsSeance().substring(3, 5));

                        //Met à jour l'affichage de l'application.
                        configureClass(Integer.parseInt(seance.getLimitePersonnes()), minimum_heure, minimum_minute);
                    }
                } else {
                    //Affiche le code de la reponse, soit le code http de la requête.
                    Log.e(TAG, "status Code: " + response.code());
                }
            }

            @Override
            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java (ici une Liste de AuaListeSeance).
             * @Param call : La requête provoquant le onFailure.
             * @Param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */
            public void onFailure(Call<List<AuaListeSeance>> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });

    }



    /**
     * Méthode pour envoyer à la base de données le changement des paramètres de temps minimum et de capacité de la séance
     *
     * Récupère le temps et la limite de personnes de la séance des paramètres pour avoir le bon type de données pour intérargir avec la base de données..
     * Créé le client permettant d'intéragir avec la base de données.
     * Utilise la méthode du client permettant l'interaction voulue avec la base de données
     * EnvoieTempsCapactie prend en paramètre une partie de l'URL et les paramètres de la séance à envoyer à la base de données.
     * Applique la requête à la base de données de façon asyncrone.
     *
     * @Param capacity          : La nouvelle capacité limite de personnes de la séance.
     * @Param minimumHours      : La partie heure du nouveau temps limite de la séance.
     * @Param minimumMinutes    : La partie minute du nouveau temps limite de la séance.
     */
    public void ModificationCapaciteHeure(int capacity, int minimumHours, int minimumMinutes) {

        //Les strings contenant le temps et la capacité de la séance
        String capacite = getString(R.string.affichageCapacite, capacity);
        String temps = getString(R.string.affichageTemps, minimumHours, minimumMinutes);


        //Créé le client permettant d'intéragir avec la base de données
        ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);

        //Utilise la méthode du client pour créer la requête permettant l'intéraction voulue avec la base de données.
        //EnvoieTempsCapactie prend en paramètre la capacité, le temps et l'id d'une séance, pour l'instant il n'y a q'une id possible : 1.

        Call<ReponseRequete> call_Post = clientRequete.EnvoieTempsCapacite(capacite, temps,"1");

        //Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack.
        //Ici le traitement de CallBack est directement appliqué :
        //  onResponse si la requête est considérée réussite(Si une réponse http esr reçu).
        //  onFailure si la requête est considérée ratée.
        call_Post.enqueue(new Callback<ReponseRequete>() {
            @Override
            //Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
            public void onResponse(Call<ReponseRequete> call, Response<ReponseRequete> reponse) {
                //Test si la requête a réussi ( code http allant de 200 à 299).
                if (reponse.isSuccessful()) {
                    //Affiche un Toast contenant le retour de la base de données sur le résultat obtenue de la requête.
                    Toast.makeText(MainActivity.this, reponse.body().getReponse() , Toast.LENGTH_SHORT).show();
                    //Met à jour les informations des paramètres d'une séance affichés par l'application (Nombre limite d'utilisateurs, Temps limite d'une séance).
                    RenseignementCapaciteHeure();
                } else {
                    //Affiche le code de la reponse, soit le code http de la requête.
                    Log.e(TAG, "status Code: " + reponse.code());
                }
            }

            @Override
            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java attendu (ici ReponseRequete).
             * @Param call : La requête provoquant le onFailure.
             * @Param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */
            public void onFailure(Call<ReponseRequete> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }
}
