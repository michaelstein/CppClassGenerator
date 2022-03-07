package com.insanefactory.cppclassgenerator;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.EmptyBorder;

import net.miginfocom.swing.MigLayout;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;

public class MainWindow extends JFrame {
	private static final long serialVersionUID = -3169917335215437579L;

	public static final Map<String, String> parentPointerClasses;
	static {
		parentPointerClasses = new HashMap<String, String>();
		parentPointerClasses.put("QtCore/QObject", "QObject");
		parentPointerClasses.put("QtCore/QThread", "QObject");
		parentPointerClasses.put("QtGui/QWidget", "QWidget");
		parentPointerClasses.put("QtOpenGl/QGLWidget", "QWidget");
	}

	private JPanel contentPane;
	private JTextField dirText;
	private JTextField classNameText;
	private JCheckBox qObjectCheck;
	private JCheckBox privateObjectCheck;
	private JSpinner tabsToSpacesSpinner;
	private JCheckBox lowerCaseCheck;
	private JComboBox<String> encodingComboBox;
	private JComboBox<String> parentBox;
	private JLabel lblDestructor;
	private JCheckBox destructorCheckBox;
	private JComboBox<String> pimplBox;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (UnsupportedLookAndFeelException e) {
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		}

		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainWindow frame = new MainWindow();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	private void generate() {
		String classname = classNameText.getText();
		String lowercase = classname.toLowerCase();
		String uppercase = classname.toUpperCase();

		int pimplType = pimplBox.getSelectedIndex();

		boolean bQObject = qObjectCheck.isSelected();
		boolean bPriv = privateObjectCheck.isSelected();
		boolean bFileNameLowerCase = lowerCaseCheck.isSelected();
		boolean bDestructor = destructorCheckBox.isSelected() || (bPriv && pimplType == 1);

		String parentInclude = parentBox.getSelectedItem().toString();
		String parentClass = parentInclude.substring(parentInclude.lastIndexOf('/') + 1);
		boolean bParent = bQObject && !parentClass.isEmpty();

		String parentPointerClass = "QObject";
		if (parentPointerClasses.containsKey(parentInclude))
			parentPointerClass = parentPointerClasses.get(parentInclude);

		/* Build header file. */
		StringBuilder sb = new StringBuilder();

		// Header gate.
		sb.append("#pragma once\n");

		// Includes.
		if (bPriv && pimplType == 0)
			sb.append("#include <memory>\n");

		if (bQObject) {
			sb.append("#include <QtCore/QObject>\n");
			if (!parentClass.equals("QObject")) {
				sb.append("#include <");
				sb.append(parentInclude);
				sb.append(">\n");
			}
		}
		sb.append("\n");

		// Class declaration beginning.
		sb.append("class ");
		sb.append(classname);
		if (bQObject) {
			sb.append(" : public ");
			sb.append(parentClass);
		}

		sb.append("\n{\n");
		if (bQObject)
			sb.append("\tQ_OBJECT\n\n");

		// Class declaration inner and end.
		sb.append("public:\n\t");
		sb.append(classname);
		sb.append("(");
		if (bQObject) {
			sb.append(parentPointerClass);
			sb.append(" *parent = nullptr");
		}
		sb.append(");\n");

		// Destructor.
		if (bDestructor) {
			sb.append("\t~");
			sb.append(classname);
			sb.append("()");
			if (bParent)
				sb.append(" override");
			sb.append(";\n");
		}

		if (bPriv) {
			sb.append("\nprivate:\n\tclass Private;\n");
			if (pimplType == 0)
				sb.append("\tstd::unique_ptr<Private> d;\n");
			else
				sb.append("\tPrivate *d;\n");
		}

		// End class definition.
		sb.append("};\n");

		String headerContent = sb.toString();

		/* Build source file. */
		sb = new StringBuilder();

		// Include.
		sb.append("#include \"");
		sb.append(bFileNameLowerCase ? lowercase : classname);
		sb.append(".h\"\n\n");

		// Private object definition.
		if (bPriv) {
			sb.append("class ");
			sb.append(classname);
			sb.append("::Private");
			sb.append("\n{\n");
			sb.append("public:\n\t");
			sb.append("Private(");
			sb.append(classname);
			sb.append(" *owner)\n\t\t: q(owner)\n\t{}\n\n\t");
			sb.append(classname);
			sb.append(" *q;\n\n");
			sb.append("};\n\n");
		}

		// Constructor definition.
		sb.append(classname);
		sb.append("::");
		sb.append(classname);
		sb.append("(");
		if (bQObject) {
			sb.append(parentPointerClass);
			sb.append(" *parent");
		}
		sb.append(")\n");

		// Constructor initialization.
		if (bQObject || bPriv)
			sb.append("\t: ");
		if (bQObject) {
			sb.append(parentClass);
			sb.append("(parent)");
		}
		if (bQObject && bPriv)
			sb.append("\n\t, ");
		if (bPriv) {
			if (pimplType == 0)
				sb.append("d(std::make_unique<Private>(this))");
			else
				sb.append("d(new Private(this))");
		}
		if (bQObject || bPriv)
			sb.append("\n");
		sb.append("{\n}\n\n");

		// Destructor.
		if (bDestructor) {
			sb.append(classname);
			sb.append("::~");
			sb.append(classname);
			sb.append("()");

			if (bPriv && pimplType == 1)
				sb.append("\n{\n\tdelete d;\n}\n");
			else
				sb.append(" = default;\n");
		}

		// Finish.
		String sourceContent = sb.toString();

		// Fix line endings.
		String newline = System.getProperty("line.separator");
		if (newline != null) {
			headerContent = headerContent.replace("\n", newline);
			sourceContent = sourceContent.replace("\n", newline);
		}

		// Check indentation settings.
		Integer tabsToSpaces = (Integer)tabsToSpacesSpinner.getValue();
		if (tabsToSpaces.intValue() >= 0) {
			int numSpaces = tabsToSpaces.intValue();
			String spaces = new String(new char[numSpaces]).replace("\0", " ");
			headerContent = headerContent.replace("\t", spaces);
			sourceContent = sourceContent.replace("\t", spaces);
		}

		/* Write files. */
		String dirpath = dirText.getText();
		File dir = new File(dirpath);
		dir.mkdirs();

		File headerFile = bFileNameLowerCase ? new File(dirpath + File.separator + lowercase + ".h") : new File(dirpath + File.separator + classname + ".h");
		File sourceFile = bFileNameLowerCase ? new File(dirpath + File.separator + lowercase + ".cpp") : new File(dirpath + File.separator + classname + ".cpp");

		String encoding = (String)encodingComboBox.getSelectedItem();
		OutputStreamWriter osw = null;
		try {
			osw = new OutputStreamWriter(new FileOutputStream(headerFile), Charset.forName(encoding).newEncoder());
			osw.write(headerContent);
			osw.close();

			osw = new OutputStreamWriter(new FileOutputStream(sourceFile), Charset.forName(encoding).newEncoder());
			osw.write(sourceContent);
			osw.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		} finally {
			try {
				if (osw != null)
					osw.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	/**
	 * Create the frame.
	 */
	public MainWindow() {
		setTitle("C++ Class Generator");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 287);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(new MigLayout("", "[][grow][]", "[][][][][][][][][]"));

		JLabel lblDirectory = new JLabel("Directory");
		contentPane.add(lblDirectory, "cell 0 0");

		dirText = new JTextField();
		contentPane.add(dirText, "cell 1 0,growx");
		dirText.setColumns(10);

		JButton dirButton = new JButton("...");
		dirButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// Create a file chooser.
				final JFileChooser fc = new JFileChooser();
				fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int returnVal = fc.showOpenDialog(MainWindow.this);

				if (returnVal != JFileChooser.APPROVE_OPTION)
					return;

				File dest = fc.getSelectedFile();
				String path = dest.getAbsolutePath();
				dirText.setText(path);
			}
		});
		contentPane.add(dirButton, "cell 2 0");

		JLabel lblClassName = new JLabel("Class Name");
		contentPane.add(lblClassName, "cell 0 1");

		classNameText = new JTextField();
		contentPane.add(classNameText, "cell 1 1 2 1,growx");
		classNameText.setColumns(10);

		JLabel lblLowerCase = new JLabel("Save as lower case");
		contentPane.add(lblLowerCase, "cell 0 2 2 1");

		lowerCaseCheck = new JCheckBox("");
		contentPane.add(lowerCaseCheck, "cell 2 2,alignx right");

		lblDestructor = new JLabel("Destructor");
		contentPane.add(lblDestructor, "cell 0 3 2 1,growx");

		destructorCheckBox = new JCheckBox("");
		contentPane.add(destructorCheckBox, "cell 2 3,alignx right");

		parentBox = new JComboBox<String>();
		parentBox.setEditable(true);
		parentBox.setModel(new DefaultComboBoxModel<String>(new String[]{ "QtCore/QObject", "QtCore/QThread", "QtGui/QWidget", "QtOpenGl/QGLWidget" }));
		contentPane.add(parentBox, "cell 0 4 2 1,growx");

		qObjectCheck = new JCheckBox("");
		qObjectCheck.setHorizontalAlignment(SwingConstants.CENTER);
		contentPane.add(qObjectCheck, "cell 2 4,alignx right");

		pimplBox = new JComboBox<String>();
		pimplBox.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getItem().equals(pimplBox.getItemAt(1)) && privateObjectCheck.isSelected()) {
					if (event.getStateChange() == ItemEvent.SELECTED) {
						destructorCheckBox.setSelected(true);
						destructorCheckBox.setEnabled(false);
					} else if (event.getStateChange() == ItemEvent.DESELECTED) {
						destructorCheckBox.setEnabled(true);
					}
				}
			}
		});
		pimplBox.setModel(new DefaultComboBoxModel<String>(new String[]{ "PIMPL - unique_ptr", "PIMPL - Raw Pointer" }));
		contentPane.add(pimplBox, "cell 0 5 2 1,growx");

		privateObjectCheck = new JCheckBox("");
		privateObjectCheck.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (privateObjectCheck.isSelected()) {
					if (pimplBox.getSelectedIndex() == 1) {
						destructorCheckBox.setSelected(true);
						destructorCheckBox.setEnabled(false);
					}
				} else {
					destructorCheckBox.setEnabled(true);
				}
			}
		});
		contentPane.add(privateObjectCheck, "cell 2 5,alignx right");

		JButton btnGenerate = new JButton("Generate");
		btnGenerate.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				generate();
			}
		});

		JLabel lblTabsToSpaces = new JLabel("Tabs to Spaces (keep tabs with -1)");
		contentPane.add(lblTabsToSpaces, "cell 0 6 2 1");

		tabsToSpacesSpinner = new JSpinner();
		tabsToSpacesSpinner.setModel(new SpinnerNumberModel(new Integer(-1), new Integer(-1), null, new Integer(1)));
		contentPane.add(tabsToSpacesSpinner, "cell 2 6,alignx center");

		JLabel lblEncoding = new JLabel("Encoding");
		contentPane.add(lblEncoding, "cell 0 7");

		encodingComboBox = new JComboBox<String>();
		encodingComboBox.setModel(new DefaultComboBoxModel<String>(new String[]{ "UTF-8", "Windows-1252" }));
		contentPane.add(encodingComboBox, "cell 1 7 2 1,growx");
		contentPane.add(btnGenerate, "cell 0 8 3 1,growx");
	}

}
