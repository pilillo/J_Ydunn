/**
 * Created by pilillo on 12/20/15.
 */


import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5SimpleWriter;
import com.bitalino.comm.BITalinoDevice;
import com.bitalino.comm.BITalinoException;
import com.bitalino.comm.BITalinoFrame;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.bluetooth.*;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Vector;

public class Ydunn extends JFrame implements DiscoveryListener, WindowListener, ActionListener, Runnable{

    private int status;
    private BITalinoDevice device;
    private StreamConnection connection;

    private boolean enabledAcquisition;
    //private int samplingWindowSize = 1000;

    // interface
    private JTabbedPane tabbedPane;
    private JList availableDevices;
    private JButton btn_connect;
    private JButton btn_refresh_devices;

    private JCheckBox chkbox_emg;
    private JCheckBox chkbox_eda;
    private JCheckBox chkbox_ecg;
    private JCheckBox chkbox_acc;
    private JCheckBox chkbox_lux;

    private int[] inputs;
    private int samplingFreq;
    private int samplesInEpoch;

    private JComboBox decimationFactor;

    private JCheckBox chkbox_txt;
    private JCheckBox chkbox_hdf5;
    private JButton btn_destFolder;
    private JTextField text_destFolder;

    private TimeSeries[] ts;

    private Writer writer;

    public Ydunn(){

        this.setTitle("Ydunn - the open Biosignal collector");
        this.setResizable(true);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.setPreferredSize(new Dimension(800, 600));
        this.setMinimumSize(new Dimension(800, 600));

        this.addWindowListener(this);

        // set appearance
        this.setBackground(Color.lightGray);
        JPanel topPanel = new JPanel();
        JScrollPane scroll = new JScrollPane(topPanel,
                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.getContentPane().add(scroll, BorderLayout.CENTER);

        tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(770,540));
        tabbedPane.addTab( "Connect", buildConnectionDetails() );

        ts = new TimeSeries[5];
        for(int i=0; i < 5; i++){
            ts[i] = new TimeSeries( "timeserie "+i );
            //ts[i].add(new Second(), new Double( 0 ) );
            ts[i].setMaximumItemAge(1000000);
        }
        tabbedPane.addTab( "EMG", createChart("EMG data", "mV", ts[0]) );
        tabbedPane.addTab( "EDA", createChart("EDA data", "mV", ts[1]) );
        tabbedPane.addTab( "ECG", createChart("ECG data", "mV", ts[2]) );
        tabbedPane.addTab( "ACC", createChart("ACC data", "g", ts[3]) );
        tabbedPane.addTab( "LUX", createChart("LUX data", "lux", ts[4]) );

        topPanel.add( tabbedPane, BorderLayout.CENTER );

        this.updateStatus(0);

        this.pack();
    }

    private ChartPanel createChart(String title, String ylabel, TimeSeries series  )
    {
        return new ChartPanel(  ChartFactory.createTimeSeriesChart(
                                title,
                                "Time",
                                ylabel,
                                new TimeSeriesCollection(series),
                                false,
                                false,
                                false));
    }

    private JPanel buildConnectionDetails(){
        JPanel connection_details = new JPanel();

        connection_details.setLayout(new BoxLayout(connection_details, BoxLayout.Y_AXIS));
        connection_details.setPreferredSize(new Dimension(760, 550));

        availableDevices = new JList(new String[0]);
        availableDevices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        availableDevices.setLayoutOrientation(JList.VERTICAL);
        availableDevices.setVisibleRowCount(-1);
        JScrollPane device_list_scroller = new JScrollPane(availableDevices);

        TitledBorder title = BorderFactory.createTitledBorder("Available Bluetooth devices:");
        title.setTitleJustification(TitledBorder.CENTER);
        availableDevices.setBorder( title );
        availableDevices.setLayout(new BoxLayout(availableDevices, BoxLayout.PAGE_AXIS));
        availableDevices.setMinimumSize(new Dimension(750, 500));
        availableDevices.setMaximumSize(new Dimension(750, 500));
        availableDevices.setAlignmentX(Component.CENTER_ALIGNMENT);
        connection_details.add(device_list_scroller);

        GridBagConstraints c = new GridBagConstraints();

        JPanel panel_selectors = new JPanel();  panel_selectors.setLayout(new BoxLayout(panel_selectors, BoxLayout.X_AXIS));
        JPanel panel_input_selection = new JPanel();
        //panel_input_selection.setLayout(new GridLayout(5, 2));
        panel_input_selection.setLayout(new GridBagLayout());
        panel_input_selection.setPreferredSize(new Dimension(120, 150));
        panel_input_selection.setMaximumSize(new Dimension(150, 150));
        TitledBorder title_input = BorderFactory.createTitledBorder("Input file formats:"); title_input.setTitleJustification(TitledBorder.CENTER); panel_input_selection.setBorder( title_input );

        chkbox_emg = new JCheckBox();   c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 0;   panel_input_selection.add(chkbox_emg,c);
        c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 0;   panel_input_selection.add(new JLabel("EMG"),c);

        chkbox_eda = new JCheckBox();   c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 1; panel_input_selection.add(chkbox_eda,c);
        c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 1;   panel_input_selection.add(new JLabel("EDA"),c);

        chkbox_ecg = new JCheckBox(); c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 2;   panel_input_selection.add(chkbox_ecg,c);
        c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 2;   panel_input_selection.add(new JLabel("ECG"),c);

        chkbox_acc = new JCheckBox(); c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 3; panel_input_selection.add(chkbox_acc,c);
        c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 3;   panel_input_selection.add(new JLabel("ACC"),c);

        chkbox_lux = new JCheckBox(); c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 4; panel_input_selection.add(chkbox_lux,c);
        c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 4;   panel_input_selection.add(new JLabel("LUX"),c);

        JPanel processing_panel = new JPanel();
        processing_panel.setLayout(new GridBagLayout());
            TitledBorder title_decimation = BorderFactory.createTitledBorder("Processing settings:");
            title_decimation.setTitleJustification(TitledBorder.CENTER);
            processing_panel.setBorder(title_decimation);
        processing_panel.setPreferredSize(new Dimension(200,50));
        processing_panel.setMaximumSize(new Dimension(250,80));
        JLabel dec_fc = new JLabel("Decimation factor:");
            c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 0;
            processing_panel.add(dec_fc, c);

            decimationFactor = new JComboBox(new String[]{"1","2","3","4","5","10","100"});
            decimationFactor.setSelectedIndex(5);
            c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 0;
            processing_panel.add(decimationFactor, c);

        JPanel panel_output_selection = new JPanel();
        //panel_output_selection.setLayout(new GridLayout(3,2));
        panel_output_selection.setLayout(new GridBagLayout());
        panel_output_selection.setPreferredSize(new Dimension(100, 100));
        panel_output_selection.setMaximumSize(new Dimension(200, 100));
        TitledBorder title_output = BorderFactory.createTitledBorder("Output file formats:"); title_output.setTitleJustification(TitledBorder.CENTER); panel_output_selection.setBorder( title_output );

        chkbox_txt = new JCheckBox();   c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 0; panel_output_selection.add(chkbox_txt,c);
        c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 0; panel_output_selection.add(new JLabel("CSV"),c);

        chkbox_hdf5 = new JCheckBox(); c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 1; panel_output_selection.add(chkbox_hdf5,c);
        c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 1; c.gridy = 1; panel_output_selection.add(new JLabel("HDF5"),c);

        text_destFolder = new JTextField(60); text_destFolder.setText(System.getProperty("user.home"));
            c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 2.0; c.gridx = 0; c.gridy = 2; c.gridwidth = 2; panel_output_selection.add(text_destFolder,c);
        btn_destFolder = new JButton("Browse");
            c.fill = GridBagConstraints.HORIZONTAL; c.gridx = 0; c.gridy = 3; c.gridwidth = 2; btn_destFolder.addActionListener(this);
            panel_output_selection.add(btn_destFolder,c);


        panel_selectors.add(panel_input_selection);
        panel_selectors.add(processing_panel);
        panel_selectors.add(panel_output_selection);
        connection_details.add(panel_selectors);
        //connection_details.add(panel_input_selection);

        JPanel conn_button_panel = new JPanel();
        conn_button_panel.setLayout(new BoxLayout(conn_button_panel, BoxLayout.X_AXIS));
        conn_button_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        connection_details.add(conn_button_panel);

        btn_refresh_devices = new JButton("Refresh");
        btn_refresh_devices.setPreferredSize(new Dimension(150, 60));
        btn_refresh_devices.addActionListener(this);

        btn_connect = new JButton("Connect");
        btn_connect.setPreferredSize(new Dimension(150,60));
        btn_connect.addActionListener(this);

        conn_button_panel.add(btn_refresh_devices);
        conn_button_panel.add(btn_connect);

        return connection_details;
    }

    private void connectTo(String mac, int samplerate, int[] inputs) throws IOException, BITalinoException {
        this.device = new BITalinoDevice(samplerate, inputs);
        // connect to BITalino device
        this.connection = (StreamConnection) Connector.open("btspp://" + mac + ":1", Connector.READ_WRITE);
        this.device.open(connection.openInputStream(), connection.openOutputStream());
    }

    private String getDeviceVersion() throws BITalinoException {
        return this.device.version();
    }

    final Object inquiryCompletedEvent = new Object();
    public static final Vector<RemoteDevice> devicesDiscovered = new Vector<RemoteDevice>();

    private void discoverDevices() throws BluetoothStateException, InterruptedException {

        devicesDiscovered.clear();

        synchronized(inquiryCompletedEvent) {
            boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, this);
            if (started) {
                System.out.println("wait for device inquiry to complete...");

                inquiryCompletedEvent.wait();

                // update interface with the found devices
                String[] items = new String[devicesDiscovered.size()];
                for(int i=0; i<items.length;i++){
                    try{
                        items[i] = devicesDiscovered.get(i).getFriendlyName(false);
                    }catch (IOException cantGetDeviceName){
                        items[i] = devicesDiscovered.get(i).getBluetoothAddress();
                    }
                }
                this.availableDevices.setListData(items);
            }
        }

    }

    private void trigger(int[] statuses){
        // trigger digital outputs
        // int[] digital = { 1, 1, 1, 1 };
        //this.device.trigger(statuses);
    }

    private void start(){
        // start acquisition on predefined analog channels
        (new Thread(this)).start();
    }

    public void stop(){
        enabledAcquisition = false;


        updateStatus(0);
    }

    private void updateStatus(int status){
        switch(status){
            case 0:
                this.status = 0;
                tabbedPane.setSelectedIndex(0);
                tabbedPane.setEnabledAt(0, true);
                for(int i= 1; i<tabbedPane.getTabCount();i++) tabbedPane.setEnabledAt(i, false);
                break;

            case 1:
                this.status = 1;
                tabbedPane.setSelectedIndex(inputs[0]+1); // enable smallest input selected
                tabbedPane.setEnabledAt(0, false);

                for(Integer i : inputs) System.out.println(i);
                for(int i= 1; i<tabbedPane.getTabCount();i++) {
                    boolean contains = false;
                    for (int j = 0; !contains && j < inputs.length; j++) {
                        if (i-1 == inputs[j]) {
                            tabbedPane.setEnabledAt(i, true);
                            contains = true;
                        }
                    }
                    //if(!contains)  tabbedPane.setEnabledAt(i, false); // already false
                }
                break;
        }
    }

    @Override
    public void run() {

        try {
            device.start();

            // read n samples, according to the selected sampling frequency
            enabledAcquisition = true;
            BITalinoFrame[] samplesRead = new BITalinoFrame[samplesInEpoch];

            int period = 1000 / samplesInEpoch;   // period in ms (within a 1sec epoch, we read #samplesInEpoch samples)

            while(enabledAcquisition){
                System.out.println("Reading " + samplesInEpoch + " samples..");

                Calendar beginning = Calendar.getInstance();
                Calendar temp = Calendar.getInstance(); temp.setTime(beginning.getTime());

                for (int counter = 0; counter < samplesInEpoch; counter++) {
                    BITalinoFrame[] frames = device.read(1);
                    samplesRead[counter] = frames[0];

                    //System.out.println("\t "+counter+": "+format.format(temp.getTime()));
                    for(Integer i : inputs){
                        ts[i].addOrUpdate(new Millisecond(temp.getTime()), samplesRead[counter].getAnalog(i));
                    }

                    this.writer.appendSample(temp.getTimeInMillis(), new double[]{frames[0].getAnalog(0), frames[0].getAnalog(1), frames[0].getAnalog(2), frames[0].getAnalog(3), frames[0].getAnalog(4)});

                    temp.setTimeInMillis(temp.getTimeInMillis()+period);    // update to the next sample time
                }

                long duration = Calendar.getInstance().getTimeInMillis() - beginning.getTimeInMillis();
                System.out.println("Epoch lasted "+duration+" ms");

                if(duration < 1000) Thread.sleep(1000 - duration);  // sleep for 1 second between epochs
            }

            // stop acquisition and close bluetooth connection
            this.device.stop();
        } catch (BITalinoException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage());
        }
    }

    public class Writer{

        private String folderpath;

        private boolean en_csvWriter;
        private FileWriter csvWriter;

        private boolean en_hdf5Writer;
        private IHDF5SimpleWriter hdf5Writer;

        public Writer(String folderpath){
            this.folderpath = folderpath;
        }

        public void enableCSVWriter(String filename) throws IOException {
            this.en_csvWriter = true;
            this.csvWriter = new FileWriter(folderpath+"/"+filename);
            this.csvWriter.append("time,EMG,EDA,ECG,ACC,LUX\n");
        }

        public void enableHDF5Writer(String filename){
            this.en_hdf5Writer = true;
            this.hdf5Writer = HDF5Factory.open(folderpath+"/"+filename);
        }

        public void appendSample(long time, //Calendar time,
                                 double[] samples) throws IOException {
            if(this.en_csvWriter){
                this.csvWriter.append(time+","  //time.getTimeInMillis()+","
                                        +samples[0]+","
                                        +samples[1]+","
                                        +samples[2]+","
                                        +samples[3]+","
                                        +samples[4]+"\n");
            }

            if(this.en_hdf5Writer){
                this.hdf5Writer.writeDoubleArray(""+time,//time.getTimeInMillis(),
                                                samples);

                //float[] mydata = new float[5];
                //writer.writeFloatArray("mydata", mydata);

                // Write a measurement as a object
                //writer.writeCompound("measurement", new Measurement(new Date(), 18.6f, 15.38937516));
                //System.out.println("Compound measurement record: "+ writer.readCompound("measurement", Measurement.class));
            }
        }

        public void closeAll() throws IOException {
            if(this.en_csvWriter){
                this.csvWriter.flush();
                this.csvWriter.close();
            }

            if(this.en_hdf5Writer){
                this.hdf5Writer.close();
            }
        }
    }


    public static void main(String[] args) throws Throwable {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                Ydunn frame = new Ydunn();
                frame.setVisible(true);
            }
        });
    }


    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        switch(actionEvent.getActionCommand()){
            case "Browse":
                JFileChooser chooser = new JFileChooser();
                    chooser.setCurrentDirectory(new java.io.File(System.getProperty("user.home")));
                    chooser.setDialogTitle("Select a destination folder");
                    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                    chooser.setAcceptAllFileFilterUsed(false);

                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    this.text_destFolder.setText(chooser.getSelectedFile().getPath());
                }
                break;

            case "Refresh":
                try {
                    discoverDevices();
                } catch (BluetoothStateException e) {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                } catch (InterruptedException e) {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
                break;

            case "Connect":
                if(this.availableDevices.getSelectedIndex() == -1){
                    JOptionPane.showMessageDialog(this, "No device selected!!");
                }else {
                    try {
                        //mapping = [('EMG',1000), ('EDA',10), ('ECG',100), ('ACC',100), ('LUX',10)]
                        samplingFreq = 10;
                        if(chkbox_emg.isSelected()) samplingFreq = 1000;
                        else if(chkbox_ecg.isSelected() || chkbox_acc.isSelected()) samplingFreq = 100;

                        this.samplesInEpoch = samplingFreq / Integer.parseInt((String)decimationFactor.getSelectedItem());
                        //this.samplesInEpoch = 10;

                        ArrayList<Integer> ins = new ArrayList<>();
                        if(chkbox_emg.isSelected()) ins.add(0);
                        if(chkbox_eda.isSelected()) ins.add(1);
                        if(chkbox_ecg.isSelected()) ins.add(2);
                        if(chkbox_acc.isSelected()) ins.add(3);
                        if(chkbox_lux.isSelected()) ins.add(4);
                        inputs = ins.stream().mapToInt(i->i).toArray();   // God bless Lambda functions!

                        if(inputs.length > 0){
                            writer = new Writer(text_destFolder.getText());
                            String filename = (new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")).format(Calendar.getInstance().getTime());
                            if(chkbox_txt.isSelected()) writer.enableCSVWriter(filename+".csv");
                            if(chkbox_hdf5.isSelected()) writer.enableHDF5Writer(filename+".hf5");

                            this.connectTo(devicesDiscovered.get(this.availableDevices.getSelectedIndex()).getBluetoothAddress(),
                                    samplingFreq,
                                    inputs);

                            this.start();
                            this.updateStatus(1);
                        }else{
                            JOptionPane.showMessageDialog(this, "You must select at least 1 channel to read data from");
                        }

                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                }
                break;
        }
    }

    @Override
    public void windowOpened(WindowEvent windowEvent) {

    }

    @Override
    public void windowClosing(WindowEvent windowEvent) {
        if(status > 0){
            enabledAcquisition = false;
            try {
                JOptionPane.showMessageDialog(this, "All data will be saved and the tool closed");
                this.writer.closeAll();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage());
            }
        }
    }

    @Override
    public void windowClosed(WindowEvent windowEvent) {

    }

    @Override
    public void windowIconified(WindowEvent windowEvent) {

    }

    @Override
    public void windowDeiconified(WindowEvent windowEvent) {

    }

    @Override
    public void windowActivated(WindowEvent windowEvent) {

    }

    @Override
    public void windowDeactivated(WindowEvent windowEvent) {

    }

    @Override
    public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
        devicesDiscovered.addElement(remoteDevice);
    }

    @Override
    public void servicesDiscovered(int i, ServiceRecord[] serviceRecords) {

    }

    @Override
    public void serviceSearchCompleted(int i, int i1) {

    }

    @Override
    public void inquiryCompleted(int i) {
        synchronized(inquiryCompletedEvent){
            inquiryCompletedEvent.notifyAll();
        }
    }
}
