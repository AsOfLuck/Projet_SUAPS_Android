package c.acdi.master.jderamaix.suaps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

/**
 *
 * Il est important de créer les éléments graphique en premier, créer ensuite l'objet Discovery
 * qui lance la recherche de l'IP du serveur, cette dernière lancera la ou les méthodes qui permettra
 * de compléter visuellement l'interface
 *
 * Dans un premier temps un objet Discovery est déclaré;
 * On lance ensuite la méthode getServerIp qui permet d'envoyer une requête et de recevoir
 * l'adresse ip du serveur
 *
 * Une interface est utilisé pour appelé une méthode particulière parce que la méthode
 * getServerIp se lance en tâche asynchrone, donc le reste de la méthode onCreate continue
 * d'être déroulé.
 *
 */
public class MainActivity extends AppCompatActivity implements InterfaceDecouverteReseau {

    /**
     * PORT : port utilisé pour le broadcast pour la recherche de l'adresse IP du serveur.
     */
    private final static int PORT = 51423;

    /**
     * Contient Toutesles valeurs et méthodes concernant la recherche de l'adresse IP par broadcast.
     * @see Discovery
     */
    private Discovery decouverte;

    /**
     * Tag repérant cet Activity dans les messages d'erreur des requêtes.
     * @see ServiceGenerator#Message(Context, String, Throwable)
     */
    private static final String TAG = "MainActivity";
    /**
     * Intervalle de temps utilisé (en secondes) pour la mise à jour périodique de l'affichage par requête.
     * L'intervalle de temps entre deux mises à jour d'affichage est de INTERVAL secondes;
     */
    private final static int INTERVAL = 5;
    /**
     * Capacité d'accueil du cours.
     */
    private int _capacity = 0;
    /**
     * Durée minimale du cours.
     */
    private String _duration = "00:00";

    /**
     * Méthode Retournant la capacité de la séance
     * @return Capacité de la sénace
     */
    public int capacity() { return _capacity; }

    /**
     * Méthode Retournant la durée de la séance
     * @return Durée de la sénace
     */
    public String duration() { return _duration; }

    /**
     * Adapteur du ReclyclerView permettant l'affichage des étudiants présents.
     */
    private StudentViewAdapter _adapter;

    /**
     *L'organisateur utilisé pour lancé la mise à jour de l'affichage à intervalle régulier.
     */
    private ScheduledExecutorService organisateur = Executors.newScheduledThreadPool(1);
    /**
     * Le gérant de l'organisateur.
     */
    private ScheduledFuture<?> organisateurGerant;


    /**
     * Méthode invoqué quand l'application est lancé.
     * Affiche tous l'affichage.
     * Puis lance la recherche de l'adresse IP et sa récupération dans les sharedPreferences.
     * Si l'adresse IP du serveur est trouvé alors les autres méthodes ajoutant des fonctionnalités concernant les requêtes sont invoquées.
     * @param savedInstanceState : contient les bundles sauvegardés, nous n'utilisons pas de bundle.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
         * On s'occupe de l'affichage avant de récupérer l'adresse IP du serveur.
         * Initialiser l'affichage des présences
         */

        _adapter = new StudentViewAdapter(this);
        RecyclerView view = findViewById(R.id.affichageEtudiants);
        view.setHasFixedSize(true);
        view.setAdapter(_adapter);
        view.setLayoutManager(new LinearLayoutManager(this));

        /*
         Méthode commençant la gestion de l'adresse IP du serveur.
        */

        decouverte = new Discovery(PORT,this);

        GestionAdresseIPServeur();

    }


    /**
     * Méthode invoqués dans le onCreate,
     * Cherche si il y a une adresse IP dans un sharedPreference et si il y en a une, la teste.
     * Sinon lance la recherche d'adresse IP.
     * @see MainActivity#onCreate(Bundle)
     */
    public void GestionAdresseIPServeur(){

        /*
        On regarde si une adresse IP est présente dans les sharedPréférences.
        Si il n'y en a pas, on lance une recherche par broadcast.
        Sinon on test si c'est celle du serveur avec une requête.
        */

        /*
        Pour reprendre des données des sharedpreferences :
        on cherche le sharedPreference correspondant au nom associé avec le sharedPreférence devant contenir l'IP puis on doit tester si il y a une valeur.
        */
        Context context = this.getApplicationContext();
        SharedPreferences sharedPref = context.getSharedPreferences(getResources().getString(R.string.NomPreferenceActivite),Context.MODE_PRIVATE);

        /*
            On met la valeur correspondant au test de la l'adresse IP à faux, on doit tester l'adresse IP dans tous les cas.
         */
        ServiceGenerator.setEtatDeLAdresseIPDuServeur(false);

        //On regarde si il existe une valeur pour ce sharedPreference à cet emplacement.
        if(sharedPref != null) {
            /*
                Deux strings utilisées pour vérifiés la valeur dans le sharedPreference.
             */
            String testPresenceDansPreference = "";
            String valeurRetour = "La valeur n'est pas présente";

            //On donne à testPresenceDansPreference la valeur dans sharedPref si il y en a une sinon on lui donne la valeur de valeurRetour.
            testPresenceDansPreference = sharedPref.getString(getResources().getString(R.string.NomPreferenceActivite), valeurRetour);

            //Si testPresenceDansPreference égale valeurRetour alors la donnée recherchée dans sharedPref n'a pas été trouvée.
            if (!testPresenceDansPreference.equals(valeurRetour)) {
                /*
                 * On a obtenu un string pouvant être l'adresse IP du serveur (normalement).
                 * On remplace donc l'IP actuelle pa ce string pour le tester.
                 */
                ServiceGenerator.setIPUrl(testPresenceDansPreference);
            }
        }

        /*
         * Création d'un objet Discovery,Sur un port particulier
         * et un un objet InterfaceDecouverteReseau, étant donné que l'activité principale
         * implémente cette interface, elle est considéré comme un objet de ce type.
         */

        //On test si l'IP est null ou égale à "" (sa valeur d'initialisation).
        if((ServiceGenerator.getIpUrl() == null) || ServiceGenerator.getIpUrl().equals("")){
            //On lance un broadcast pour obtenir une autre adresse IP.

            RechercheAdresseIP();
            } else {
            //On doit maintenant tester l'adresse IP.
                LancementRequeteValidationIP();
            }
    }

    /**
     * Méthode ajoutant le onSwiped sur le RecyclerView pour pouvoir enlerver des utilisateurs de la séance.
     */
    public void AjoutOnSwipedSurRecyclerView(){
        RecyclerView view = findViewById(R.id.affichageEtudiants);
        /*
         * Implémenter la suppression de présences par swipe
         */
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView view, RecyclerView.ViewHolder holder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder holder, int direction) {

                //On prend l'id de l'utilsateur allant être enlevé.
                int numero_id_utilisateur = _adapter.get((int) holder.itemView.getTag()).id();

                //Méthode lançant la requete utilisée pour enlever l'utilisateur donné de la séance.
                LancementRequeteSupprimerUtilisateur(numero_id_utilisateur);
            }
        }).attachToRecyclerView(view);
    }

    /**
     * Méthode créant et initialisant le gérant de l'organisateur s'occupant des mises à jour automatique de l'affichage.
     */
    public void InitialiserGerantOrganisateur(){
        /*
         * Créer un gérant d'organisateur et son organisateur fixé à une intervalle de INTERVAL d'unité de temps appellant AppelRun.
         */
        organisateurGerant = organisateur.scheduleAtFixedRate(AppelRun,0,INTERVAL,TimeUnit.SECONDS);
        /*
         * Rafraichit l'affichage de la configuration de la séance.
         */
        }


    /**
     * Méthode appelant la méthode lançant la recherche de l'adresse IP du serveur.
     */
    public void RechercheAdresseIP(){
        decouverte.getServerIp();
    }

    /**
     * Méthode de l'interface InterfaceDecouverteReseau
     * Est activée lorsque une adresse IP est reçu ou que le temps alloué pour la réception est dépassé.
     * Si le string représentant l'IP est null alors le broadcast n'a pas atteint le serveur,
     *      il faut donc relancer une recherche par broadcast
     * Sinon il faut tester si c'est l'adresse iP du serveur.
     *
     * @see InterfaceDecouverteReseau#recuperationIpServer()
     * @see Discovery.NetworkAsync#onPostExecute(String)
     */
    @Override
    public void recuperationIpServer() {
        //On prend le string Reçu.
        String s = decouverte.getIp();

        //On test si le string est null.
        if (s == null || s.equals("null")){
            //Si il l'est on relance une recherche de l'adresse IP.
            RechercheAdresseIP();
        } else {
            //Sinon on change l'IP du ServiceGenerator et on lance le test.
            ServiceGenerator.setIPUrl(s);
            LancementRequeteValidationIP();
        }
    }

    /**
     * Méthode sauvegardant l'adresse IP du serveur dans un sharedPreferences. Cela permet de tester
     * directement une adresse IP au redémarrage de l'application.
     */
    public void SauvegardeAdresseIP(){
            /*
             * Maintenant que l'on a l'adresse IP du serveur, on peut l'écrire dans un sharedPreferences pour la "sauvegarder".
             * Pour écrire des données dans un sharedpreference :
             * On cherche le sharedPreference correspondant au nom associé avec le sharedPreférence devant contenir l'IP.
             * Puis on doit regarder si il est instancié, si oui on peut enlever l'ancien IP et rajouter le nouveau
             *          sinon on doit l'instancier et ajouter l'IP.
             * Pour finir il faut appliqué les modifications.
            */
        Context context = this.getApplicationContext();
        SharedPreferences sharedPref = context.getSharedPreferences(getResources().getString(R.string.NomPreferenceActivite),Context.MODE_PRIVATE);

        //Si sharedPref est null, il faut l'instancier.
        if(sharedPref == null) {
            //On l'instancie.
            sharedPref = this.getPreferences(Context.MODE_PRIVATE);
            //On créer un editor nous permettant de l'éditer.
            SharedPreferences.Editor editor = sharedPref.edit();
            //On ajoute dedans à la clé référencé par R.string.NomPreferenceActivite la valeur de l'IP du serveur.
            editor.putString(getString(R.string.NomPreferenceActivite), ServiceGenerator.getIpUrl());
            //On applique les changements.
            editor.apply();
        } else {
            /*
             * sharedPref n'est pas null, nous pouvons donc directement l'éditer.
             * Pour écrire des données dans un sharedpreference.
             */
            //On prend un editor nous permettant de l'éditer.
            SharedPreferences.Editor editor = sharedPref.edit();
            //On enlève ce qui se trouvait déjà à l'emplacement donné par la clé.
            editor.remove(getString(R.string.NomPreferenceActivite));
            //On rajoute au même emplacement l'adressse IP du serveur.
            editor.putString(getString(R.string.NomPreferenceActivite), ServiceGenerator.getIpUrl());
            //On applique les changements.
            editor.apply();
        }

        //On peut maintenant implémenter et appliquer toutes les méthodes concernant le serveur.
        //Ici la méthode appelant les méthodes chargés de l'affichage.
        InvocationMethodeInitialisation();

    }

    /**
     * Méthode invoquer après la validification de l'adresse IP du serveur.
     * Cette méthode va lancer consécutivement différentes tâches:<br>
     *     - Ajouter la fonctionnalité au recyclerView lui permettant d'enlever des éléments avec un swipe<br>
     *     - Mise à jour de l'affichage sur la capacité et la durée minimal d'une séance<br>
     *     - Mise à jour du recyclerView pour obtenir les élèves présent.<br>
     *     - Création de l'organisateur, ce dernier est ce qui permet à l'affichage de se mettre à jour
     *     automatiquement<br>
     */
    public void InvocationMethodeInitialisation(){
        AjoutOnSwipedSurRecyclerView();
        RenseignementCapaciteHeure();
        ReinitialiseAffichage();
        InitialiserGerantOrganisateur();
    }

    /**
     * Lance la mise à jour de l'affichage,
     * invoqué quand MainActivity revient en premier plan,
     * soit quand l'utilisateur était sur une autre application
     * soit en revenant de RFIDActivity.
     */
    @Override
    public void onResume() {
        super.onResume();
        /*
         * Toutes les INTERVAL secondes, applique la méthode AppelRun.
         * Seulement si l'adresse IP a été vérifier.
         * Si le gérant de l'organisateur est arrêté, on le relance.
         */
        if(ServiceGenerator.getEtatDeLAdresseIPDuServeur() && organisateurGerant.isCancelled()){
            organisateurGerant = organisateur.scheduleAtFixedRate(AppelRun, 0, INTERVAL, TimeUnit.SECONDS);
            ReinitialiseAffichage();
        }
    }


    /**
     * Méthode invoqué quand MainActivity va en second plan,
     * Arrête la mise à jour automatique de l'affichage.
     */
    @Override
    protected void onPause(){
        super.onPause();
        //Si le gérant de l'organisateur n'est pas arrêté et que l'adresse IP est la bonne(si elle ne l'est pas, il n'a jamais été initialisé), on l'arrête.
        if(ServiceGenerator.getEtatDeLAdresseIPDuServeur() && !organisateurGerant.isCancelled()) {
            organisateurGerant.cancel(true);
        }
    }

    /**
     * Méthode invoqué quand l'activité est détruite,
     * Détruit l'organisateur.
     */
    @Override
    protected void onDestroy(){
        /*
         * Si l'organisateur n'est pas shutdown, on le shutdown.
         */
        if(!organisateur.isShutdown()) {
            organisateur.shutdown();
        }
        super.onDestroy();
    }

    /**
     * Créer un runnable lançant la mise à jour de l'affichage.
     */
    final Runnable AppelRun = new Runnable(){
        public void run() {
            /*
             * Appelle la méthode mettant à jour les utilisateurs à partir de la base de données par une requête.
             */
            ReinitialiseAffichage();
            /*
             * Appelle la méthode mettant à jour les paramètres(temps limite, capacité).
             */
            RenseignementCapaciteHeure();
        }
    };

    /**
     * Met à jour l'affichage des présences,
     * créée pour factoriser cette opération qui est plus fréquente que prévue.
     */
    private void _updateAttendance() {
        ((TextView) findViewById(R.id.affichageOccupation)).setText(
                getString(R.string.affichageOccupation, _adapter.getItemCount(), _capacity)
        );
    }

    /**
     * Lance l'activité permettant de badger,
     * invoqué comme callback du bouton boutonBadge.
     *
     * @param view View appeler au moment de l'interraction sur le bouton
     */
    public void Badger(View view) {
        Intent intent = new Intent(this, RFIDActivity.class);
        startActivity(intent);
    }

    /**
     * Lance le dialogue d'ajout manuel,
     * invoqué comme callback du bouton boutonAjout.
     *
     *  @param view View appeler au moment de l'interraction sur le bouton
     */
    public void ajouterEtudiant(View view) {
        new AddStudentDialog().show(getSupportFragmentManager(), "ajoutEtudiant");
    }

    /**
     * Ajoute un étudiant manuellement à la séance en :
     *   Créant le client et un receptacle de la méthode permettant l'intéraction voulue avec la base de données
     *   Puis appliquant la méthode asynchronement et réinitialisant l'affichage si le résultat est réussi
     * @param firstName Le prénom de l'étudiant à ajouter.
     * @param lastName  Le nom de l'étudiant à ajouter.
     */
    public void addStudent(String firstName, String lastName) {
        /*
         * Créé le client permettant d'interargir avec la base de données
         */
        ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);

        /*
         * Créer le receptacle de la méthode voulue à partir de clientRequetes
         * EnvoieNom prend en paramètre le nom et le prénom de l'utilisateur.
         */
        Call<ReponseRequete> call_Post = clientRequete.EnvoieNom(lastName, firstName);

        /*
         * Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack
         * Ici le traitement de CallBack est directement appliqué :
         * onResponse si la requête est considérée réussite(Si une réponse http esr reçu).
         * onFailure si la requête est considérée ratée.
         */
        call_Post.enqueue(new Callback<ReponseRequete>() {
            /**
             *
             *Méthode étant appliqué lorsque la requête est reçu par la base de données.
             *Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
             *
             * @param call Requête provoquant le onResponse
             * @param reponse Réponse de la base du serveur
             */
            @Override
            public void onResponse(Call<ReponseRequete> call, Response<ReponseRequete> reponse) {
                //Test si la requête a réussi ( code http allant de 200 à 299).
                if (reponse.isSuccessful()) {
                    Toast.makeText(MainActivity.this, reponse.body().getReponse() , Toast.LENGTH_SHORT).show();
                    ReinitialiseAffichage();
                } else {
                    //Affiche le code de la reponse, soit le code http de la requête.
                    Log.e(TAG,"Status code : " + reponse.code());
               }
            }

            /*
             * Si la requête n'est pas arrivé jusqu'à la base de données
             */
            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java (ici un ReponseRequete).
             * @param call : La requête provoquant le onFailure.
             * @param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */
            @Override
            public void onFailure(Call<ReponseRequete> call, Throwable t) {
                /*
                 * Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision
                 */
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }

    /**
     * Met à jour l'affichage en :
     *   Demandant la vue à la base
     *   Mettant à jour atomiquement l'adaptateur si la vue reçue n'est pas vide.
     */
    public void ReinitialiseAffichage() {

        /*
         * Créer le client permettant d'intérargir avec la base de données
         */
        ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);

        /*
         * Utilise la méthode du client pour créer la requête permettant l'interaction avec la base de données
         * RecoitPersonnes ne prend pas de paramètre
         */
        Call<List<ModeleUtilisateur>> methodeCall = clientRequete.RecoitPersonnes();

        /*
         * Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack
         * Ici le traitement de CallBack est directement appliqué :
         * onResponse si la requête est considérée réussite(Si une réponse http esr reçu).
         * onFailure si la requête est considérée ratée.
         */
        methodeCall.enqueue(new Callback<List<ModeleUtilisateur>>() {
            @Override
            /**
             *
             * Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la gestion de la requête par la base de données.
             */
            public void onResponse(Call<List<ModeleUtilisateur>> call, Response<List<ModeleUtilisateur>> response) {
                //Test si la requête a réussi (code http allant de 200 à 299).
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
                                        getResources().getString(R.string.affichageNomEtudiant, etudiant.getNom().toUpperCase(), etudiant.getPrenom()),
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
             * @param call : La requête provoquant le onFailure.
             * @param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */
            @Override
            public void onFailure(Call<List<ModeleUtilisateur>> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }

    /**
     * Méthode permettant de créer le menu d'option en haut à droit de l'application
     *
     * @see AppCompatActivity#onCreateOptionsMenu(Menu)
     * */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.cours_menu, menu);
        return true;
    }

    /**
     * Méthode se déclenchant lorsqu'une action est effectué sur le menu d'options
     *
     * @see AppCompatActivity#onOptionsItemSelected(MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.configurerCours:
                configurerCours(null);
                return true;
            case R.id.calendar:
                    Toast msg = Toast.makeText(this,"Calendar page",Toast.LENGTH_LONG);
                    msg.show();
                return true;
            case R.id.synchron:
                Toast msg2 = Toast.makeText(this,"Synchron page",Toast.LENGTH_LONG);
                msg2.show();
                return true;
            case R.id.setting:
                Toast msg3 = Toast.makeText(this,"Setting page",Toast.LENGTH_LONG);
                msg3.show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Lance le dialogue de configuration de classe,
     * invoqué comme option du menu principal.
     *
     * @param view View appelé au moment de l'interraction avec le bouton "configurer"
     */
    public void configurerCours(View view) {
        new ConfigDialog().show(getSupportFragmentManager(),"configClasse");
    }

    /**
     * Change la capacité et le temps minimum de la séance.
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

    /**
     * Renseigne la configuration actuelle de la séance en la demandant à la base de données.
     */
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
                if(response.isSuccessful()){

                    //Prend la partie de response correspondant à l'objet envoyé par la base de données.
                    List<AuaListeSeance> listeSeance = response.body();

                    if(!listeSeance.isEmpty()) {
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

            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java (ici une Liste de AuaListeSeance).
             * @param call : La requête provoquant le onFailure.
             * @param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */
            @Override
            public void onFailure(Call<List<AuaListeSeance>> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });

    }



    /**
     * Met à jour la configuration de la séance en l'envoyant à la base de données.
     *
     * Récupère le temps et la limite de personnes de la séance des paramètres pour avoir le bon type de données pour intérargir avec la base de données..
     * Créé le client permettant d'intéragir avec la base de données.
     * Utilise la méthode du client permettant l'interaction voulue avec la base de données
     * EnvoieTempsCapactie prend en paramètre une partie de l'URL et les paramètres de la séance à envoyer à la base de données.
     * Applique la requête à la base de données de façon asyncrone.
     *
     * @param capacity          : La nouvelle capacité limite de personnes de la séance.
     * @param minimumHours      : La partie heure du nouveau temps limite de la séance.
     * @param minimumMinutes    : La partie minute du nouveau temps limite de la séance.
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

            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java attendu (ici ReponseRequete).
             * @param call : La requête provoquant le onFailure.
             * @param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */
            @Override
            public void onFailure(Call<ReponseRequete> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }




    /**
     * Prépare et lance la requête utilisé pour vérifier si l'adresse IP actuelle est bien la bonne,
     * Si oui, alors on change le boolean s'en chargeant à vrai.
     * Si non on change le boolean s'en chargeant à faux.
     */
    public void LancementRequeteValidationIP(){
        //Créé le client utilisé pour intérargir avec la base de données.
        ClientRequetes clientRequete = ServiceGenerator.createService(ClientRequetes.class);

        //Créer le receptacle de la méthode voulue à partir de clientRequete
        //Envoie une requête pour vérifier si c'est la bonne adresse IP du serveur.
        Call<ReponseRequete> call_Post = clientRequete.TestBonneAdresseIP();

        //Méthode envoyant la requête asynchronement à la base de données et stockant la réponse obtenue (erreur ou réussite) dans CallBack
        //Ici le traitement de CallBack est directement appliqué :
        //  onResponse si la requête est considérée réussite(Si une réponse http est reçu).
        //  onFailure si la requête est considérée ratée.

        call_Post.enqueue(new Callback<ReponseRequete>() {
            @Override
            //Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
            public void onResponse(Call<ReponseRequete> call, Response<ReponseRequete> response) {
                //Test si la requête a réussi ( code http allant de 200 à 299).
                if (response.isSuccessful()) {
                    //Affiche le succès de la vérification de l'adresse IP du serveur.
                    if(!response.body().getReponse().equals("null")){
                        Toast.makeText(MainActivity.this, response.body().getReponse(), Toast.LENGTH_SHORT).show();
                    }
                    //Changement du boolean s'occupant de l'état de l'adresse IP du serveur.
                    ServiceGenerator.setEtatDeLAdresseIPDuServeur(true);
                    //Nous devons maintenant passer à la suite de la gestion de l'adresse IP.
                    SauvegardeAdresseIP();
                } else {
                    //Affiche le code de la reponse, soit le code http de la requête.
                    Log.e(TAG, "Status code : " + response.code());
                    //Des erreurs se sont passés, on doit donc changer le boolean chargé de son état.
                    ServiceGenerator.setEtatDeLAdresseIPDuServeur(false);
                    //On doit ensuite relancer la recherche de l'adresse IP, nous ne sommes pas sûr que ce soit la bonne.
                }
            }
            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java (ne peut pas causer de problème ici, aucune réponse n'est attendu).
             * @param call : La requête provoquant le onFailure.
             * @param t    : objet contenant le message et le code d'erreur provoqué par la requête.
             */

            @Override
            public void onFailure(Call<ReponseRequete> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                ServiceGenerator.Message(MainActivity.this, TAG, t);
                //Ce n'est pas l'adresse IP du serveur, il faut donc changer la valeur du boolean s'en chargeant.
                ServiceGenerator.setEtatDeLAdresseIPDuServeur(false);
                //On doit ensuite relancer la recherche de l'adresse IP.
                RechercheAdresseIP();
            }
        });

    }


    /**
     * Méthode utilisant une requête pour enlever l'utilisateur désigné par l'id.
     * @param numero_id_utilisateur : le numéro de l'utilisateur a enlever.
     */
    public void LancementRequeteSupprimerUtilisateur(int numero_id_utilisateur){

        String numero_id_chaine = "" + numero_id_utilisateur;

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
            /**
             *Méthode étant appliqué lorsque la requête est reçu par la base de données. Mais attention, il peut toujours y avoir des problèmes ayant occurés lors de la requête.
             */
            @Override
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
            /**
             * Méthode étant appliqué losque des problèmes sont apparus lors de  :
             *   - la connexion au serveur,
             *   - la création de la requête,
             *   - la transformation de la réponse en objet java (ne peut pas causer de problème ici, aucune réponse n'est attendu).
             * @param call  La requête provoquant le onFailure.
             * @param t     objet contenant le message et le code d'erreur provoqué par la requête.
             */
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                //Méthode affichant les messages pour l'utilisateur en cas de onFailure, voir ServiceFenerator pour plus de précision.
                ServiceGenerator.Message(MainActivity.this, TAG, t);
            }
        });
    }



}

