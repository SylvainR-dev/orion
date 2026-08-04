import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';

import { PersonDetailsComponent } from './person-details.component';
import { PersonService, Person } from '../person.service';
import { OrganizationService, Organization } from '../organization.service';

describe('PersonDetailsComponent', () => {
  let component: PersonDetailsComponent;
  let fixture: ComponentFixture<PersonDetailsComponent>;
  let personServiceSpy: jasmine.SpyObj<PersonService>;
  let organizationServiceSpy: jasmine.SpyObj<OrganizationService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const makePerson = (overrides: Partial<Person> = {}): Person => ({
    id: 1,
    firstName: 'Jane',
    lastName: 'Doe',
    email: 'jane@doe.com',
    phone: '',
    bio: '',
    createdAt: new Date(),
    organizations: [],
    ...overrides,
  });

  const makeOrg = (overrides: Partial<Organization> = {}): Organization => ({
    id: 10,
    name: 'Orion',
    createdAt: new Date(),
    personIds: [],
    persons: [],
    ...overrides,
  });

  function makeActivatedRoute(personId: string | null): Partial<ActivatedRoute> {
    return {
      snapshot: {
        paramMap: convertToParamMap(personId !== null ? { personId } : {})
      } as any
    };
  }

  function configure(personIdParam: string | null) {
    personServiceSpy = jasmine.createSpyObj('PersonService', ['fetchById', 'save', 'deleteById']);
    organizationServiceSpy = jasmine.createSpyObj('OrganizationService', ['fetchAll', 'addPerson', 'removePerson']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    organizationServiceSpy.fetchAll.and.resolveTo([makeOrg({ id: 10 }), makeOrg({ id: 20 })]);
    personServiceSpy.fetchById.and.resolveTo(makePerson());
    personServiceSpy.save.and.resolveTo(makePerson());
    personServiceSpy.deleteById.and.resolveTo();
    organizationServiceSpy.addPerson.and.resolveTo();
    organizationServiceSpy.removePerson.and.resolveTo();

    TestBed.configureTestingModule({
      imports: [PersonDetailsComponent],
      providers: [
        { provide: PersonService, useValue: personServiceSpy },
        { provide: OrganizationService, useValue: organizationServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: makeActivatedRoute(personIdParam) },
      ]
    });

    fixture = TestBed.createComponent(PersonDetailsComponent);
    component = fixture.componentInstance;
  }

  it('should create', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();
    expect(component).toBeTruthy();
  }));

  it('constructor should load all organizations', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    expect(organizationServiceSpy.fetchAll).toHaveBeenCalled();
    expect(component.organizations.length).toBe(2);
  }));

  it('ngOnInit should set isNew when personId param is "new"', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    expect(component.isNew).toBeTrue();
    expect(personServiceSpy.fetchById).not.toHaveBeenCalled();
  }));

  it('ngOnInit should fetch the person when personId param is numeric', fakeAsync(() => {
    configure('1');
    const person = makePerson({ id: 1 });
    personServiceSpy.fetchById.and.resolveTo(person);

    fixture.detectChanges();
    tick();

    expect(personServiceSpy.fetchById).toHaveBeenCalledWith(1);
    expect(component.person).toEqual(person);
    expect(component.isNew).toBeFalse();
  }));

  it('ngOnInit should do nothing when personId param is absent', fakeAsync(() => {
    configure(null);
    fixture.detectChanges();
    tick();

    expect(personServiceSpy.fetchById).not.toHaveBeenCalled();
    expect(component.isNew).toBeFalse();
  }));

  it('savePerson should save and navigate when creating a new person', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    const saved = makePerson({ id: 5 });
    personServiceSpy.save.and.resolveTo(saved);

    component.savePerson();
    tick();

    expect(personServiceSpy.save).toHaveBeenCalled();
    expect(component.person).toEqual(saved);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['persons', 5]);
  }));

  it('savePerson should save without navigating when updating an existing person', fakeAsync(() => {
    configure('1');
    const existing = makePerson({ id: 1 });
    personServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    personServiceSpy.save.and.resolveTo(existing);
    component.savePerson();
    tick();

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  }));

  it('deletePerson should do nothing when person has no id', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    component.deletePerson();
    tick();

    expect(personServiceSpy.deleteById).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  }));

  it('deletePerson should delete and navigate home when person has an id', fakeAsync(() => {
    configure('1');
    const existing = makePerson({ id: 1 });
    personServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    component.deletePerson();
    tick();

    expect(personServiceSpy.deleteById).toHaveBeenCalledWith(1);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['']);
  }));

  it('addSelectedOrganization should do nothing when no organization is selected', fakeAsync(() => {
    configure('1');
    const existing = makePerson({ id: 1 });
    personServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    component.selectedOrganization = null;
    component.addSelectedOrganization();
    tick();

    expect(organizationServiceSpy.addPerson).not.toHaveBeenCalled();
  }));

  it('addSelectedOrganization should call addPerson and refresh the person', fakeAsync(() => {
    configure('1');
    const existing = makePerson({ id: 1 });
    personServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    component.selectedOrganization = makeOrg({ id: 10 });
    const refreshed = makePerson({ id: 1, organizations: [makeOrg({ id: 10 })] });
    personServiceSpy.fetchById.and.resolveTo(refreshed);

    component.addSelectedOrganization();
    tick();

    expect(organizationServiceSpy.addPerson).toHaveBeenCalledWith(10, 1);
    expect(personServiceSpy.fetchById).toHaveBeenCalledWith(1);
    expect(component.person).toEqual(refreshed);
  }));

  it('removeOrganization should do nothing when the organization has no id', fakeAsync(() => {
    configure('1');
    const existing = makePerson({ id: 1 });
    personServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    component.removeOrganization(makeOrg({ id: undefined }));
    tick();

    expect(organizationServiceSpy.removePerson).not.toHaveBeenCalled();
  }));

  it('removeOrganization should call removePerson and refresh the person', fakeAsync(() => {
    configure('1');
    const existing = makePerson({ id: 1 });
    personServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    component.removeOrganization(makeOrg({ id: 10 }));
    tick();

    expect(organizationServiceSpy.removePerson).toHaveBeenCalledWith(10, 1);
  }));

  it('refresh should do nothing when person has no id', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    component.refresh();
    tick();

    expect(personServiceSpy.fetchById).not.toHaveBeenCalled();
  }));
});