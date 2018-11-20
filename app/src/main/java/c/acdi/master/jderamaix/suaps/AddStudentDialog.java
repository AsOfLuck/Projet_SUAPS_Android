package c.acdi.master.jderamaix.suaps;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

/**
 * Classe représentant la dialogue d'ajout manuel d'étudiant à la base.
 */
public class AddStudentDialog extends DialogFragment {

    public AddStudentDialog() {
        super();
    }

    /**
     * @see DialogFragment#onCreateDialog(Bundle)
     */
    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        /*
         * On utilise explicitement la classe MainActivity à la place de la simple Activity car ce
         * dialogue d'ajout est déjà spécifique à l'application de toute manière :
         * inutile d'abstraire ceux avec qui on communique
         */
        final MainActivity activity = (MainActivity) getActivity();
        final View view = LayoutInflater.from(activity).inflate(R.layout.dialog_add, null);

        return new AlertDialog.Builder(activity)
                .setView(view)
                .setPositiveButton(R.string.etiquetteAjoutAjouter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        activity.addStudent(
                                ((EditText) view.findViewById(R.id.saisiePrenomEtudiant)).getText().toString(),
                                ((EditText) view.findViewById(R.id.saisieNomEtudiant)).getText().toString()
                        );
                    }
                })
                .setNegativeButton(R.string.etiquetteAjoutAnnuler, null)
                .create();
    }

}