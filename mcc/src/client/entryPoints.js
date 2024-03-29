module.exports = {
  apps: [{
    name: 'dashboard',
    title: 'MCC Data',
    permission: 'read',
    path: './src/client/Dashboard'
  },{
    name: 'dashboardWebpart',
    title: 'MCC Data',
    permission: 'read',
    path: './src/client/Dashboard/webpart',
    generateLib: true
  },{
    name: 'animalRequest',
    title: 'Animal Requests',
    permission: 'read',
    path: './src/client/AnimalRequest'
  }, {
    name: 'requestReview',
    title: 'Animal Request Review',
    permission: 'read',
    template: 'home',
    path: './src/client/RequestReview'
  },{
    name: 'geneticsPlot',
    title: 'Marmoset Genetics',
    permission: 'read',
    path: './src/client/GeneticsPlot'
  },{
    name: 'geneticsPlotWebpart',
    title: 'Marmoset Genetics',
    permission: 'read',
    path: './src/client/GeneticsPlot/webpart'
  }]
};
