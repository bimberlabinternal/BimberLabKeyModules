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
  }, {
    name: 'geneticsPlotWebpart',
    title: 'Marmoset Genetics',
    permission: 'read',
    path: './src/client/GeneticsPlot/webpart'
  },{
    name: 'u24Dashboard',
    title: 'U24 Dashboard',
    permissionClasses: ['org.labkey.api.security.permissions.ReadPermission'],
    path: './src/client/U24Dashboard'
  },{
    name: 'u24DashboardWebpart',
    title: 'U24 Dashboard',
    permissionClasses: ['org.labkey.api.security.permissions.ReadPermission'],
    path: './src/client/U24Dashboard/webpart',
    generateLib: true
  }]
};
